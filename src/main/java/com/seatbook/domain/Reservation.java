package com.seatbook.domain;

import java.util.List;

public record Reservation(String id, String showId, String userId, String idempotencyKey, String requestHash,
                          List<String> seats, long amountPaise, String status) {
    public Reservation withStatus(String s) {
        return new Reservation(id, showId, userId, idempotencyKey, requestHash, seats, amountPaise, s);
    }
}
