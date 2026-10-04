package com.seatbook.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.seatbook.config.AppProperties;
import com.seatbook.error.DeclineException;
import com.seatbook.error.Reason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The ONLY local cache: an in-process, TTL-bounded seat lock table.
 *
 * Why it is small: an entry exists only for a seat that was touched in the last few seconds
 * (IN_FLIGHT while a reserve is running, CONFIRMED briefly after it commits). No show metadata,
 * no seat lists, no reservations. Hard-capped by maximumSize and by TTL.
 *
 * What it does: admission control. One attempt per seat reaches the DB at a time; everyone else is turned
 * away without taking a connection. Acquisition NEVER blocks (putIfAbsent, multi-seat in sorted order,
 * all-or-nothing release on conflict), so it cannot deadlock.
 *
 * What it is not: the source of truth. The guarded UPDATE in the DB still decides. If an entry is lost
 * (TTL, eviction, restart) the worst case is an extra DB attempt that loses cleanly.
 */
@Component
public class SeatLockCache {

    /** Identity-compared on purpose, so remove(key, lock)/replace(key, lock, ..) only touch OUR entry. */
    public static final class SeatLock {
        final String idem;          // userId|idempotencyKey of the owner; null = "taken by someone else"
        final boolean confirmed;
        SeatLock(String idem, boolean confirmed) { this.idem = idem; this.confirmed = confirmed; }
    }

    public record Held(String key, SeatLock lock) {}

    private final Cache<String, SeatLock> cache;
    private final Counter rejected;

    public SeatLockCache(AppProperties props, MeterRegistry registry) {
        var c = props.seatLock();
        long inflight = c.inflightTtl().toNanos(), confirmed = c.confirmedTtl().toNanos();
        this.cache = Caffeine.newBuilder()
                .maximumSize(c.maxEntries())
                .expireAfter(new Expiry<String, SeatLock>() {
                    long ttl(SeatLock v) { return v.confirmed ? confirmed : inflight; }
                    @Override public long expireAfterCreate(String k, SeatLock v, long now) { return ttl(v); }
                    @Override public long expireAfterUpdate(String k, SeatLock v, long now, long cur) { return ttl(v); }
                    @Override public long expireAfterRead(String k, SeatLock v, long now, long cur) { return cur; }
                })
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(registry, cache, "seatLocks");
        this.rejected = Counter.builder("seatlock.rejected").description("Reserves refused by the local seat lock").register(registry);
    }

    private static String key(String showId, String seat) { return showId + "|" + seat; }

    /**
     * Try to lock every seat (callers pass them sorted). Returns the entries WE created.
     * A seat already locked by the same idempotency identity is skipped (not ours to release): that request is a
     * retry/duplicate and must reach the DB, where the unique key turns it into a replay.
     * Any other holder => release what we took, count it, and decline with SEAT_TAKEN.
     */
    public List<Held> tryAcquire(String showId, List<String> sortedSeats, String idem) {
        List<Held> held = new ArrayList<>(sortedSeats.size());
        for (String seat : sortedSeats) {
            String k = key(showId, seat);
            SeatLock mine = new SeatLock(idem, false);
            SeatLock prev = cache.asMap().putIfAbsent(k, mine);
            if (prev == null) {
                held.add(new Held(k, mine));
            } else if (prev.idem == null || !prev.idem.equals(idem)) {
                release(held);
                rejected.increment();
                throw new DeclineException(Reason.SEAT_TAKEN);
            }
        }
        return held;
    }

    /** After commit: IN_FLIGHT -> CONFIRMED (short TTL), so late arrivals are refused without touching the DB. */
    public void confirm(List<Held> held, String idem) {
        for (Held h : held) cache.asMap().replace(h.key(), h.lock(), new SeatLock(idem, true));
    }

    /** DB said the (single) seat is already confirmed to someone else: leave a short-lived refusal marker. */
    public void markTaken(Held h) {
        cache.asMap().replace(h.key(), h.lock(), new SeatLock(null, true));
    }

    public void release(List<Held> held) {
        for (Held h : held) cache.asMap().remove(h.key(), h.lock());
    }

    /** Cancel: drop CONFIRMED entries for the seats (never an in-flight lock someone else holds). */
    public void evictConfirmed(String showId, List<String> seats) {
        for (String seat : seats)
            cache.asMap().computeIfPresent(key(showId, seat), (k, v) -> v.confirmed ? null : v);
    }
}
