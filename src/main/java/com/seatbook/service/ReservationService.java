package com.seatbook.service;

import com.seatbook.cache.SeatLockCache;
import com.seatbook.domain.CancelOutcome;
import com.seatbook.domain.Reservation;
import com.seatbook.domain.ReserveResult;
import com.seatbook.error.ApiException;
import com.seatbook.error.DeclineException;
import com.seatbook.error.Reason;
import com.seatbook.metrics.BookingMetrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Partial-request policy: ALL-OR-NOTHING. Either every requested seat is claimed in one transaction or none is
 * (rollback => 409 seat-taken). No best-effort mode, so there is nothing to reconcile under concurrency.
 */
@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final BookingTx bookingTx;
    private final SeatLockCache seatLocks;
    private final BookingMetrics metrics;

    public ReservationService(BookingTx bookingTx, SeatLockCache seatLocks, BookingMetrics metrics) {
        this.bookingTx = bookingTx; this.seatLocks = seatLocks; this.metrics = metrics;
    }

    public ReserveResult reserve(String userId, String showId, List<String> rawSeats, String key) {
        try {
            ReserveResult r = doReserve(userId, showId, rawSeats, key);
            if (r.replayed()) metrics.declined("idempotent-replay");
            return r;
        } catch (DeclineException d) {
            metrics.declined(d.reason().label);
            log.info("reserve declined user={} show={} reason={}", userId, showId, d.reason().label);
            throw d;
        }
    }

    private ReserveResult doReserve(String userId, String showId, List<String> rawSeats, String key) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw ApiException.badRequest("idempotency key required (max 128 chars)");
        List<String> seats = SeatLabels.normalize(rawSeats);          // sorted + deduped = lock order
        String hash = sha256(showId + "|" + String.join(",", seats));
        String idem = userId + "|" + key;

        // 1) local seat lock: non-blocking, all-or-nothing. Losers of a hot-seat race stop here, no DB connection used.
        List<SeatLockCache.Held> held = seatLocks.tryAcquire(showId, seats, idem);
        boolean confirmed = false;
        try {
            // 2) the real, atomic decision in the DB (each attempt = one short transaction)
            for (int attempt = 1; ; attempt++) {
                try {
                    Reservation r = bookingTx.reserve(showId, userId, key, hash, seats);
                    seatLocks.confirm(held, idem);
                    confirmed = true;
                    metrics.confirmed();
                    metrics.adjustAvailable(showId, -seats.size());
                    log.info("reserve confirmed user={} show={} seats={} reservation={}", userId, showId, seats, r.id());
                    return new ReserveResult(r, false);
                } catch (DeclineException d) {
                    if (d.reason() == Reason.SEAT_TAKEN && seats.size() == 1 && !held.isEmpty())
                        seatLocks.markTaken(held.get(0));              // confirmed elsewhere: brief fast-refusal marker
                    throw d;
                } catch (DataIntegrityViolationException dup) {
                    // Unique (user, key) hit: the other tx committed first. If the row exists it is a replay.
                    Optional<Reservation> existing = bookingTx.findByUserKey(userId, key);
                    if (existing.isEmpty()) throw dup;                  // some other integrity problem
                    return replayOrConflict(existing.get(), hash);
                } catch (RuntimeException e) {
                    if (!Retries.isRetryable(e) || attempt >= MAX_ATTEMPTS) throw e;   // exhausted => 500, visible in metrics
                    log.warn("retryable db failure attempt={} : {}", attempt, e.getClass().getSimpleName());
                    Retries.backoff(attempt);
                }
            }
        } finally {
            if (!confirmed) seatLocks.release(held);                   // never leave a lock behind on any non-success path
        }
    }

    private ReserveResult replayOrConflict(Reservation existing, String hash) {
        if (!existing.requestHash().equals(hash)) throw new DeclineException(Reason.IDEMPOTENCY_KEY_REUSED);
        return new ReserveResult(existing, true);
    }

    public Reservation cancel(String userId, String reservationId) {
        for (int attempt = 1; ; attempt++) {
            try {
                CancelOutcome o = bookingTx.cancel(userId, reservationId);
                if (o.released() > 0) {                                        // post-commit side effects only
                    Reservation res = o.reservation();
                    seatLocks.evictConfirmed(res.showId(), res.seats());
                    metrics.cancelled();
                    metrics.adjustAvailable(res.showId(), o.released());
                    log.info("cancel ok user={} reservation={} released={}", userId, res.id(), o.released());
                }
                return o.reservation();
            } catch (RuntimeException e) {
                if (!Retries.isRetryable(e) || attempt >= MAX_ATTEMPTS) throw e;
                Retries.backoff(attempt);
            }
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
