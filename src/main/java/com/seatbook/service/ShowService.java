package com.seatbook.service;

import com.seatbook.config.AppProperties;
import com.seatbook.domain.ShowMeta;
import com.seatbook.domain.ShowState;
import com.seatbook.error.ApiException;
import com.seatbook.metrics.BookingMetrics;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Not transactional itself; DB work goes through ShowStore. Nothing here is retained between requests. */
@Service
public class ShowService {
    private final ShowStore store;
    private final AppProperties props;
    private final BookingMetrics metrics;
    /** In-flight loads only (removed on completion): concurrent GETs of one show share a single DB read. */
    private final ConcurrentHashMap<String, CompletableFuture<ShowState>> inflight = new ConcurrentHashMap<>();

    public ShowService(ShowStore store, AppProperties props, BookingMetrics metrics) {
        this.store = store; this.props = props; this.metrics = metrics;
    }

    public ShowState create(String name, List<String> rawSeats, long pricePaise, Integer perUserLimit) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("name required");
        if (pricePaise < 0) throw ApiException.badRequest("price_paise must be >= 0");
        List<String> seats = SeatLabels.normalize(rawSeats);
        if (seats.size() > props.maxSeatsPerShow()) throw ApiException.badRequest("too many seats");
        int limit = perUserLimit == null ? props.defaultPerUserLimit() : perUserLimit;
        if (limit < 1) throw ApiException.badRequest("per_user_limit must be >= 1");

        String id = UUID.randomUUID().toString();
        store.create(id, name.trim(), pricePaise, limit, seats);
        metrics.setAvailable(id, seats.size());
        // Response built from what we just wrote (all available): no re-read through the scarce pool.
        return new ShowState(new ShowMeta(id, name.trim(), pricePaise, limit), seats.size(), 0, 0,
                seats.stream().map(s -> new ShowState.SeatStatus(s, "available")).toList());
    }

    /**
     * Single-flight, not a cache: the first caller loads, concurrent callers join that load, and the entry is
     * removed as soon as it completes. A joiner may see a snapshot that started a few ms before it arrived;
     * the snapshot is still internally consistent (available+held+confirmed==total).
     */
    public ShowState state(String showId) {
        CompletableFuture<ShowState> mine = new CompletableFuture<>();
        CompletableFuture<ShowState> other = inflight.putIfAbsent(showId, mine);
        if (other != null) {
            try { return other.join(); }
            catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }
        try {
            ShowState s = store.loadState(showId).orElseThrow(() -> ApiException.notFound("show not found"));
            mine.complete(s);
            return s;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(showId, mine);
        }
    }
}
