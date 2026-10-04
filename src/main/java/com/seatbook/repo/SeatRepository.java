package com.seatbook.repo;

import com.seatbook.entity.SeatEntity;
import com.seatbook.entity.SeatId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<SeatEntity, SeatId> {

    /** THE atomic decision: one guarded bulk UPDATE. Returned row count must equal the seats requested. */
    @Modifying
    @Query("update SeatEntity s set s.status = 'confirmed', s.reservationId = :rid, s.userId = :uid "
            + "where s.id.showId = :sid and s.status = 'available' and s.id.seatLabel in :seats")
    int claim(@Param("sid") String showId, @Param("seats") Collection<String> seats,
              @Param("rid") String reservationId, @Param("uid") String userId);

    /** Guarded on reservation_id AND status: can never free a seat that now belongs to someone else. */
    @Modifying
    @Query("update SeatEntity s set s.status = 'available', s.reservationId = null, s.userId = null "
            + "where s.reservationId = :rid and s.status = 'confirmed'")
    int release(@Param("rid") String reservationId);

    /** Failure path only: tells "seat taken" apart from "seat does not exist". */
    @Query("select count(s) from SeatEntity s where s.id.showId = :sid and s.id.seatLabel in :seats")
    long countExisting(@Param("sid") String showId, @Param("seats") Collection<String> seats);

    @Query("select new com.seatbook.repo.SeatRow(s.id.seatLabel, s.status) from SeatEntity s "
            + "where s.id.showId = :sid order by s.id.seatLabel")
    List<SeatRow> findSeatRows(@Param("sid") String showId);

    @Query("select s.id.showId, count(s) from SeatEntity s where s.status = 'available' group by s.id.showId")
    List<Object[]> countAvailableByShow();
}
