package com.seatbook.service;

import com.seatbook.domain.ShowMeta;
import com.seatbook.domain.ShowState;
import com.seatbook.entity.SeatEntity;
import com.seatbook.entity.ShowEntity;
import com.seatbook.repo.SeatRepository;
import com.seatbook.repo.SeatRow;
import com.seatbook.repo.ShowRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for show data. Each method = one short transaction; reads are readOnly and return DTOs. */
@Component
public class ShowStore {
    private static final int FLUSH_EVERY = 1000;   // Hibernate splits each flush into jdbc.batch_size (50) statements

    private final ShowRepository shows;
    private final SeatRepository seats;
    private final EntityManager em;

    public ShowStore(ShowRepository shows, SeatRepository seats, EntityManager em) {
        this.shows = shows; this.seats = seats; this.em = em;
    }

    @Transactional
    public void create(String id, String name, long pricePaise, int perUserLimit, List<String> labels) {
        em.persist(new ShowEntity(id, name, pricePaise, perUserLimit, labels.size()));
        em.flush();                                    // parent row first (FK), regardless of order_inserts
        int i = 0;
        for (String label : labels) {
            em.persist(new SeatEntity(id, label));
            if (++i % FLUSH_EVERY == 0) { em.flush(); em.clear(); }   // keep the persistence context small
        }
        em.flush();
        em.clear();
    }

    /** Counts are derived from the same row set as the seat list, so available+held+confirmed==total by construction. */
    @Transactional(readOnly = true)
    public Optional<ShowState> loadState(String showId) {
        Optional<ShowMeta> meta = shows.findMeta(showId);
        if (meta.isEmpty()) return Optional.empty();
        List<SeatRow> rows = seats.findSeatRows(showId);
        int a = 0, h = 0, c = 0;
        for (SeatRow r : rows) {
            switch (r.status()) { case "available" -> a++; case "held" -> h++; default -> c++; }
        }
        return Optional.of(new ShowState(meta.get(), a, h, c,
                rows.stream().map(r -> new ShowState.SeatStatus(r.seat(), r.status())).toList()));
    }

    /** Two queries, one tx: all show ids (default 0) + grouped available counts. Used by the reconciler. */
    @Transactional(readOnly = true)
    public Map<String, Integer> availableByShow() {
        Map<String, Integer> out = new HashMap<>();
        for (String id : shows.findAllIds()) out.put(id, 0);
        for (Object[] r : seats.countAvailableByShow()) out.put((String) r[0], ((Number) r[1]).intValue());
        return out;
    }
}
