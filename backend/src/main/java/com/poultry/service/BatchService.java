package com.poultry.service;

import com.poultry.domain.*;
import com.poultry.repo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {
    private final BatchRepository batches;
    private final StockMovementRepository movements;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public BatchService(BatchRepository b, StockMovementRepository m, AuditService a, CurrentUser c) {
        batches = b; movements = m; audit = a; currentUser = c;
    }

    public record RegisterBatch(String batchCode, Long supplierId, LocalDate arrivalDate, String breed,
                                int initialQuantity, BigDecimal purchaseCost) {}
    public record BatchView(Long id, String batchCode, LocalDate arrivalDate, String breed,
                            int initialQuantity, long currentStock, BigDecimal purchaseCost, String status) {}

    /** Workflow A: batch + exactly one opening movement + audit row, in one transaction. */
    @Transactional
    public BatchView register(RegisterBatch r) {
        if (batches.existsByBatchCode(r.batchCode()))
            throw new BusinessException(HttpStatus.CONFLICT, "Batch code already exists");
        Long userId = currentUser.id();

        PoultryBatch b = new PoultryBatch();
        b.setBatchCode(r.batchCode()); b.setSupplierId(r.supplierId()); b.setArrivalDate(r.arrivalDate());
        b.setBreed(r.breed()); b.setInitialQuantity(r.initialQuantity()); b.setPurchaseCost(r.purchaseCost());
        b.setCreatedBy(userId);
        b = batches.saveAndFlush(b);

        movements.save(StockMovement.of(b.getId(), StockMovement.Type.OPENING,
            r.initialQuantity(), "Opening stock", userId));
        audit.record("BATCH_REGISTERED", "poultry_batches", b.getId(), r.batchCode() + " x" + r.initialQuantity());
        return view(b);
    }

    @Transactional(readOnly = true)
    public List<BatchView> list() { return batches.findAll().stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    public BatchView get(Long id) {
        return view(batches.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found")));
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history(Long id) { return movements.findByBatchIdOrderByIdDesc(id); }

    private BatchView view(PoultryBatch b) {
        return new BatchView(b.getId(), b.getBatchCode(), b.getArrivalDate(), b.getBreed(),
            b.getInitialQuantity(), movements.currentStock(b.getId()), b.getPurchaseCost(), b.getStatus().name());
    }
}
