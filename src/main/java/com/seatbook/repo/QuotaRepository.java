package com.seatbook.repo;

import com.seatbook.entity.QuotaEntity;
import com.seatbook.entity.QuotaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotaRepository extends JpaRepository<QuotaEntity, QuotaId> {

    /** Guarded upsert: 1 row = within limit, 0 rows = would exceed limit. Caller guarantees n <= lim. */
    @Modifying
    @Query(nativeQuery = true, value =
            "MERGE user_show_quota WITH (HOLDLOCK) AS t "
          + "USING (SELECT :sid AS show_id, :uid AS user_id) AS s ON t.show_id = s.show_id AND t.user_id = s.user_id "
          + "WHEN MATCHED AND t.active_seats + :n <= :lim THEN UPDATE SET active_seats = t.active_seats + :n "
          + "WHEN NOT MATCHED THEN INSERT (show_id, user_id, active_seats) VALUES (s.show_id, s.user_id, :n);")
    int bump(@Param("sid") String showId, @Param("uid") String userId, @Param("n") int n, @Param("lim") int limit);

    @Modifying
    @Query("update QuotaEntity q set q.activeSeats = q.activeSeats - :n "
            + "where q.id.showId = :sid and q.id.userId = :uid and q.activeSeats >= :n")
    int decrement(@Param("sid") String showId, @Param("uid") String userId, @Param("n") int n);
}
