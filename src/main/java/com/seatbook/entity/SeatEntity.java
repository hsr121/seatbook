package com.seatbook.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Seats are never loaded as entities on the hot path; state changes are guarded bulk UPDATEs (see SeatRepository). */
@Entity
@Table(name = "seats")
public class SeatEntity {
    @EmbeddedId private SeatId id;
    private String status;
    private String reservationId;
    private String userId;

    protected SeatEntity() {}
    public SeatEntity(String showId, String seatLabel) {
        this.id = new SeatId(showId, seatLabel);
        this.status = "available";
    }
}
