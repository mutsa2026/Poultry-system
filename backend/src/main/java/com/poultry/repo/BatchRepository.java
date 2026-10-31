package com.poultry.repo;

import com.poultry.domain.PoultryBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BatchRepository extends JpaRepository<PoultryBatch, Long> {
    boolean existsByBatchCode(String batchCode);

    /** Row lock: serialises every stock-changing operation on the same batch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PoultryBatch b where b.id = :id")
    Optional<PoultryBatch> findByIdForUpdate(@Param("id") Long id);
}
