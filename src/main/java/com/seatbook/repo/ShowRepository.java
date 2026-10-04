package com.seatbook.repo;

import com.seatbook.domain.ShowMeta;
import com.seatbook.entity.ShowEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<ShowEntity, String> {

    @Query("select new com.seatbook.domain.ShowMeta(s.id, s.name, s.pricePaise, s.perUserLimit) from ShowEntity s where s.id = :id")
    Optional<ShowMeta> findMeta(@Param("id") String id);

    @Query("select s.id from ShowEntity s")
    List<String> findAllIds();
}
