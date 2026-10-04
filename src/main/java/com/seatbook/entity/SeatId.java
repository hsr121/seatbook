package com.seatbook.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SeatId implements Serializable {
    private String showId;
    private String seatLabel;

    protected SeatId() {}
    public SeatId(String showId, String seatLabel) { this.showId = showId; this.seatLabel = seatLabel; }

    @Override public boolean equals(Object o) {
        return o instanceof SeatId s && Objects.equals(showId, s.showId) && Objects.equals(seatLabel, s.seatLabel);
    }
    @Override public int hashCode() { return Objects.hash(showId, seatLabel); }
}
