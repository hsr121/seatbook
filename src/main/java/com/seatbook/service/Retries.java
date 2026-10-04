package com.seatbook.service;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;

final class Retries {
    private Retries() {}

    /** Deadlock victim (1205) / lock timeout (1222), however Hibernate/Spring happened to wrap it. */
    static boolean isRetryable(Throwable t) {
        for (Throwable c = t; c != null; c = (c.getCause() == c ? null : c.getCause())) {
            if (c instanceof PessimisticLockingFailureException || c instanceof TransientDataAccessException) return true;
            if (c instanceof SQLException s && (s.getErrorCode() == 1205 || s.getErrorCode() == 1222)) return true;
        }
        return false;
    }

    static void backoff(int attempt) {
        try { Thread.sleep(ThreadLocalRandom.current().nextLong(5L * attempt, 25L * attempt)); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
