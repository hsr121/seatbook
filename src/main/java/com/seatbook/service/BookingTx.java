package com.seatbook.service;

import com.seatbook.domain.CancelOutcome;
import com.seatbook.domain.Reservation;
import com.seatbook.domain.ShowMeta;
import com.seatbook.entity.ReservationEntity;
import com.seatbook.error.ApiException;
import com.seatbook.error.DeclineException;
import com.seatbook.error.Reason;
import com.seatbook.repo.QuotaRepository;
import com.seatbook.repo.ReservationRepository;
import com.seatbook.repo.ReservationRow;
import com.seatbook.repo.SeatRepository;
import com.seatbook.repo.ShowRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place the booking transactions live. No caches, metrics, logging of business events or other I/O in
 * here, so a connection is held for the DB statements only. RuntimeExceptions (incl. DeclineException) roll back.
 * Retry loops live OUTSIDE (ReservationService) so each attempt is a fresh transaction through this proxy.
 *
 * Lock order, same in reserve and cancel:  reservations row -> seats (PK order) -> quota row.
 */
@Component
public class BookingTx {
    private final ShowRepository shows;
    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final QuotaRepository quotas;

    public BookingTx(ShowRepository shows, ReservationRepository reservations, SeatRepository seats,
                     QuotaRepository quotas) {
        this.shows = shows; this.reservations = reservations; this.seats = seats; this.quotas = quotas;
    }

    @Transactional
    public Reservation reserve(String showId, String userId, String key, String hash, List<String> sortedSeats) {
        // Same connection as the rest of the tx; PK seek, no locks. Nothing about the show is cached in the JVM.
        ShowMeta show = shows.findMeta(showId).orElseThrow(() -> ApiException.notFound("show not found"));
        if (sortedSeats.size() > show.perUserLimit()) throw new DeclineException(Reason.PER_USER_LIMIT);

        String id = UUID.randomUUID().toString();
        long amount = Math.multiplyExact(show.pricePaise(), (long) sortedSeats.size());

        // saveAndFlush => the INSERT runs NOW (first lock; unique key = idempotency guard), not at commit.
        reservations.saveAndFlush(new ReservationEntity(id, show.id(), userId, key, hash,
                String.join(",", sortedSeats), amount));

        int claimed = seats.claim(show.id(), sortedSeats, id, userId);
        if (claimed != sortedSeats.size()) {
            // failure path only: unknown seat label (400) vs seat already confirmed (409)
            if (seats.countExisting(show.id(), sortedSeats) != sortedSeats.size())
                throw ApiException.badRequest("unknown seat for this show");
            throw new DeclineException(Reason.SEAT_TAKEN);          // all-or-nothing rollback
        }

        if (quotas.bump(show.id(), userId, sortedSeats.size(), show.perUserLimit()) == 0)
            throw new DeclineException(Reason.PER_USER_LIMIT);      // rolls back the claim too

        return new Reservation(id, show.id(), userId, key, hash, sortedSeats, amount, "confirmed");
    }

    @Transactional
    public CancelOutcome cancel(String userId, String reservationId) {
        if (reservations.markCancelled(reservationId, userId) == 1) {
            ReservationRow row = reservations.findRowById(reservationId).orElseThrow();
            int released = seats.release(reservationId);
            quotas.decrement(row.showId(), userId, released);
            return new CancelOutcome(row.toDomain().withStatus("cancelled"), released);
        }
        ReservationRow row = reservations.findRowById(reservationId)
                .orElseThrow(() -> ApiException.notFound("reservation not found"));
        if (!row.userId().equals(userId)) throw ApiException.forbidden("not your reservation");
        return new CancelOutcome(row.toDomain(), 0);                 // already cancelled: idempotent no-op
    }

    @Transactional(readOnly = true)
    public Optional<Reservation> findByUserKey(String userId, String key) {
        return reservations.findRowByUserKey(userId, key).map(ReservationRow::toDomain);
    }
}
