package com.poultry.service;

import com.poultry.domain.*;
import com.poultry.repo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MortalityService {
    private final BatchRepository batches;
    private final StockMovementRepository movements;
    private final MortalityRepository mortality;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public MortalityService(BatchRepository b, StockMovementRepository m, MortalityRepository mo,
                            AuditService a, CurrentUser c) {
        batches = b; movements = m; mortality = mo; audit = a; currentUser = c;
    }

    public record ReportDeaths(LocalDate eventDate, int quantity, String cause, String notes) {}
    public record MortalityReport(Long batchId, LocalDate from, LocalDate to, long deaths,
                                  int initialQuantity, BigDecimal ratePercent) {}

    /**
     * FR-17/19/27: lock the batch row, check stock, write one ledger movement and one event.
     * The row lock makes concurrent reports/sales on the same batch run one at a time.
     */
    @Transactional
    public MortalityEvent report(Long batchId, ReportDeaths r) {
        PoultryBatch batch = batches.findByIdForUpdate(batchId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (batch.getStatus() != PoultryBatch.Status.ACTIVE)
            throw new BusinessException(HttpStatus.CONFLICT, "Batch is closed");
        if (r.eventDate().isBefore(batch.getArrivalDate()) || r.eventDate().isAfter(LocalDate.now().plusDays(1)))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Event date is outside the batch's lifetime");

        long stock = movements.currentStock(batchId);
        if (r.quantity() > stock)
            throw new BusinessException(HttpStatus.CONFLICT,
                "Reported deaths (" + r.quantity() + ") exceed live stock (" + stock + ")");

        Long userId = currentUser.id();
        StockMovement m = movements.saveAndFlush(StockMovement.of(
            batchId, StockMovement.Type.MORTALITY, -r.quantity(), r.cause(), userId));
        MortalityEvent e = mortality.save(MortalityEvent.of(
            batchId, r.eventDate(), r.quantity(), r.cause(), r.notes(), m.getId(), userId));
        audit.record("MORTALITY_REPORTED", "mortality_events", e.getId(),
            "batch=" + batch.getBatchCode() + " qty=" + r.quantity() + " cause=" + r.cause());
        return e;
    }

    /** FR-20 denominator (documented): deaths in period / batch initial quantity, as a percentage. */
    @Transactional(readOnly = true)
    public MortalityReport report(Long batchId, LocalDate from, LocalDate to) {
        PoultryBatch b = batches.findById(batchId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found"));
        long deaths = mortality.totalDeaths(batchId, from, to);
        BigDecimal rate = BigDecimal.valueOf(deaths * 100L)
            .divide(BigDecimal.valueOf(b.getInitialQuantity()), 2, RoundingMode.HALF_UP);
        return new MortalityReport(batchId, from, to, deaths, b.getInitialQuantity(), rate);
    }
}
