package com.seatbook.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class QuotaId implements Serializable {
    private String showId;
    private String userId;

    protected QuotaId() {}
    public QuotaId(String showId, String userId) { this.showId = showId; this.userId = userId; }

    @Override public boolean equals(Object o) {
        return o instanceof QuotaId q && Objects.equals(showId, q.showId) && Objects.equals(userId, q.userId);
    }
    @Override public int hashCode() { return Objects.hash(showId, userId); }
}
