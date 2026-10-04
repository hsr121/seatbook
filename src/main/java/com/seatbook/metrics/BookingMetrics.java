package com.seatbook.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Exposed: reservations_confirmed_total, reservations_declined_total{reason}, reservations_cancelled_total,
 *          seats_available{show_id}, seats_reconcile_drift{show_id}.
 * seats_available is an in-memory gauge (no DB hit on scrape), updated after each commit and
 * corrected by the reconciler; drift is exported so a leak shows up on a dashboard.
 */
@Component
public class BookingMetrics {
    private final MeterRegistry registry;
    private final Counter confirmed;
    private final Counter cancelled;
    private final Map<String, AtomicInteger> available = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> drift = new ConcurrentHashMap<>();

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.confirmed = Counter.builder("reservations.confirmed").description("Confirmed reservations").register(registry);
        this.cancelled = Counter.builder("reservations.cancelled").register(registry);
        for (String r : new String[]{"seat-taken", "per-user-limit", "idempotent-replay", "idempotency-key-reused"})
            registry.counter("reservations.declined", "reason", r);
    }

    public void confirmed() { confirmed.increment(); }
    public void cancelled() { cancelled.increment(); }
    public void declined(String reason) { registry.counter("reservations.declined", "reason", reason).increment(); }

    public void adjustAvailable(String showId, int delta) { gauge(showId).addAndGet(delta); }
    public void setAvailable(String showId, int value) {
        int old = gauge(showId).getAndSet(value);
        driftGauge(showId).set(Math.abs(old - value));
    }

    private AtomicInteger gauge(String showId) {
        return available.computeIfAbsent(showId, id -> {
            var a = new AtomicInteger();
            registry.gauge("seats.available", Tags.of("show_id", id), a);
            return a;
        });
    }

    private AtomicInteger driftGauge(String showId) {
        return drift.computeIfAbsent(showId, id -> {
            var a = new AtomicInteger();
            registry.gauge("seats.reconcile.drift", Tags.of("show_id", id), a);
            return a;
        });
    }
}
