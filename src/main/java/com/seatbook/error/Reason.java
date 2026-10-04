package com.seatbook.error;

public enum Reason {
    SEAT_TAKEN("seat-taken"),
    PER_USER_LIMIT("per-user-limit"),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused");

    public final String label;
    Reason(String label) { this.label = label; }
}
