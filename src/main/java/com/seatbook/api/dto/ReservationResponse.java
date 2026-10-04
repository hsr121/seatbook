package com.seatbook.api.dto;

import com.seatbook.domain.Reservation;
import java.util.List;

public record ReservationResponse(String reservationId, String showId, String userId, List<String> seats,
                                  long amountPaise, String status) {
    public static ReservationResponse of(Reservation r) {
        return new ReservationResponse(r.id(), r.showId(), r.userId(), r.seats(), r.amountPaise(), r.status());
    }
}
