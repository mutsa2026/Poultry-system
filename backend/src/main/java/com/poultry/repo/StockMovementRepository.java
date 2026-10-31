package com.poultry.repo;

import com.poultry.domain.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    @Query("select coalesce(sum(m.quantityChange), 0) from StockMovement m where m.batchId = :batchId")
    long currentStock(@Param("batchId") Long batchId);

    List<StockMovement> findByBatchIdOrderByIdDesc(Long batchId);
}
