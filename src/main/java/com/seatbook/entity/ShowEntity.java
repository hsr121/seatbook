package com.seatbook.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shows")
public class ShowEntity {
    @Id private String id;
    private String name;
    private long pricePaise;
    private int perUserLimit;
    private int totalSeats;
    // created_at is DB-defaulted and deliberately unmapped

    protected ShowEntity() {}

    public ShowEntity(String id, String name, long pricePaise, int perUserLimit, int totalSeats) {
        this.id = id; this.name = name; this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit; this.totalSeats = totalSeats;
    }
}
