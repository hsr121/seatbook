package com.seatbook.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Persistable so save() issues a plain INSERT (assigned id would otherwise trigger merge = extra SELECT).
 * UNIQUE(user_id, idempotency_key) in the schema is the exactly-once guard.
 */
@Entity
@Table(name = "reservations")
public class ReservationEntity implements Persistable<String> {
    @Id private String id;
    private String showId;
    private String userId;
    private String idempotencyKey;
    private String requestHash;
    private String seats;
    private long amountPaise;
    private String status;

    @Transient private boolean fresh = true;

    protected ReservationEntity() {}

    public ReservationEntity(String id, String showId, String userId, String idempotencyKey, String requestHash,
                             String seatsCsv, long amountPaise) {
        this.id = id; this.showId = showId; this.userId = userId; this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash; this.seats = seatsCsv; this.amountPaise = amountPaise;
        this.status = "confirmed";
    }

    @Override public String getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    @PostLoad @PostPersist void markNotNew() { fresh = false; }
}
