package com.poultry.repo;

import com.poultry.domain.MortalityEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MortalityRepository extends JpaRepository<MortalityEvent, Long> {
    List<MortalityEvent> findByBatchIdOrderByEventDateDesc(Long batchId);

    @Query("select coalesce(sum(e.quantity), 0) from MortalityEvent e " +
           "where e.batchId = :batchId and e.eventDate between :from and :to")
    long totalDeaths(@Param("batchId") Long batchId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
