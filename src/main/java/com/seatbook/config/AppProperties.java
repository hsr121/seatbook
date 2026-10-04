package com.seatbook.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
        String jwtSecret,
        String adminBootstrapSecret,
        boolean devTokensEnabled,
        int defaultPerUserLimit,
        int maxSeatsPerShow,
        SeatLock seatLock,
        Duration reconcileInterval) {

    /** inflightTtl must exceed Hikari connection-timeout + tx time so a queued attempt keeps its lock. */
    public record SeatLock(Duration inflightTtl, Duration confirmedTtl, long maxEntries) {}
}
