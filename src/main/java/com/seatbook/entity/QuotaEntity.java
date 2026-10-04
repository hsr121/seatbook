package com.seatbook.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_show_quota")
public class QuotaEntity {
    @EmbeddedId private QuotaId id;
    private int activeSeats;

    protected QuotaEntity() {}
}
