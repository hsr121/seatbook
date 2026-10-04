package com.seatbook.repo;

import com.seatbook.domain.Reservation;
import java.util.Arrays;

public record ReservationRow(String id, String showId, String userId, String idempotencyKey, String requestHash,
                             String seats, long amountPaise, String status) {
    public Reservation toDomain() {
        return new Reservation(id, showId, userId, idempotencyKey, requestHash,
                Arrays.asList(seats.split(",")), amountPaise, status);
    }
}
