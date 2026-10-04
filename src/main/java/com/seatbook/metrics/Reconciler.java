package com.seatbook.metrics;

import com.seatbook.service.ShowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background job: re-derive seats_available from the DB so the gauge reconciles with API state. */
@Component
public class Reconciler {
    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);
    private final ShowStore store;
    private final BookingMetrics metrics;

    public Reconciler(ShowStore store, BookingMetrics metrics) { this.store = store; this.metrics = metrics; }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { reconcile(); }

    @Scheduled(fixedDelayString = "${app.reconcile-interval:5s}")
    public void reconcile() {
        try {
            store.availableByShow().forEach(metrics::setAvailable);
        } catch (Exception e) {
            log.warn("reconcile failed: {}", e.toString());
        }
    }
}
