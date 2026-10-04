package com.seatbook.repo;

import com.seatbook.entity.ReservationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {

    String PROJECTION = "select new com.seatbook.repo.ReservationRow(r.id, r.showId, r.userId, r.idempotencyKey, "
            + "r.requestHash, r.seats, r.amountPaise, r.status) from ReservationEntity r ";

    @Query(PROJECTION + "where r.id = :id")
    Optional<ReservationRow> findRowById(@Param("id") String id);

    @Query(PROJECTION + "where r.userId = :uid and r.idempotencyKey = :key")
    Optional<ReservationRow> findRowByUserKey(@Param("uid") String userId, @Param("key") String key);

    /** Conditional on owner AND state; takes the row lock that starts the cancel lock order. */
    @Modifying
    @Query("update ReservationEntity r set r.status = 'cancelled' "
            + "where r.id = :id and r.userId = :uid and r.status = 'confirmed'")
    int markCancelled(@Param("id") String id, @Param("uid") String userId);
}
