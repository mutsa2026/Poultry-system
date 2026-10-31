package com.poultry.processing;

import com.poultry.domain.PoultryBatch;
import com.poultry.repo.BatchRepository;
import com.poultry.repo.StockMovementRepository;
import com.poultry.service.AuditService;
import com.poultry.service.BusinessException;
import com.poultry.service.BusinessTime;
import com.poultry.service.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DressedProcessingService {
    public record ProcessBatch(Long sourceBatchId, LocalDate processedDate, int birdsProcessed,
            BigDecimal dressedKg, String notes) {}
    public record DressedBatch(Long id, String batchNo, Long sourceBatchId, String sourceBatchCode,
            LocalDate processedDate, int birdsProcessed, BigDecimal dressedKg, BigDecimal stockKg,
            String notes) {}

    private final JdbcTemplate jdbc;
    private final BatchRepository batches;
    private final StockMovementRepository liveMovements;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final BusinessTime time;

    public DressedProcessingService(JdbcTemplate jdbc, BatchRepository batches, StockMovementRepository liveMovements,
            AuditService audit, CurrentUser currentUser, BusinessTime time) {
        this.jdbc = jdbc;
        this.batches = batches;
        this.liveMovements = liveMovements;
        this.audit = audit;
        this.currentUser = currentUser;
        this.time = time;
    }

    @Transactional
    public DressedBatch process(ProcessBatch request) {
        if (request.sourceBatchId() == null)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose the live-bird batch to process");
        if (request.birdsProcessed() <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Birds processed must be above 0");
        if (request.dressedKg() == null || request.dressedKg().signum() <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Dressed weight must be above 0 kg");
        if (request.dressedKg().stripTrailingZeros().scale() > 3)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Dressed weight can have at most 3 decimal places");

        PoultryBatch source = batches.findByIdForUpdate(request.sourceBatchId())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Live-bird batch not found"));
        if (source.getStatus() != PoultryBatch.Status.ACTIVE)
            throw new BusinessException(HttpStatus.CONFLICT, "The live-bird batch is closed");
        if (request.processedDate() == null || request.processedDate().isBefore(source.getArrivalDate())
                || request.processedDate().isAfter(time.today()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Processing date must be between the batch arrival date and today");

        long liveStock = liveMovements.currentStock(source.getId());
        if (request.birdsProcessed() > liveStock)
            throw new BusinessException(HttpStatus.CONFLICT, "Only " + liveStock + " birds are alive in that batch");

        Long userId = currentUser.id();
        String batchNo = jdbc.queryForObject(
            "SELECT 'PROC-' || lpad(nextval('dressed_batch_no_seq')::text, 6, '0')", String.class);
        Long id = jdbc.queryForObject("""
            INSERT INTO dressed_chicken_batches(batch_no, source_batch_id, processed_date, birds_processed, dressed_kg, notes, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, batchNo, source.getId(), request.processedDate(), request.birdsProcessed(),
            request.dressedKg(), request.notes(), userId);

        jdbc.update("""
            INSERT INTO stock_movements(batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
            VALUES (?, 'PROCESSING', ?, 'dressed_chicken_batches', ?, ?, ?)
            """, source.getId(), -request.birdsProcessed(), id, batchNo, userId);
        jdbc.update("""
            INSERT INTO dressed_stock_movements(dressed_batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
            VALUES (?, 'PROCESSING', ?, 'dressed_chicken_batches', ?, ?, ?)
            """, id, request.dressedKg(), id, batchNo, userId);
        audit.record("DRESSED_BATCH_PROCESSED", "dressed_chicken_batches", id,
            batchNo + " source=" + source.getBatchCode() + " birds=" + request.birdsProcessed() + " kg=" + request.dressedKg());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<DressedBatch> list() {
        return jdbc.query("""
            SELECT d.id, d.batch_no, d.source_batch_id, b.batch_code, d.processed_date,
                   d.birds_processed, d.dressed_kg,
                   COALESCE(SUM(m.quantity_change), 0) AS stock_kg, d.notes
            FROM dressed_chicken_batches d
            JOIN poultry_batches b ON b.id = d.source_batch_id
            LEFT JOIN dressed_stock_movements m ON m.dressed_batch_id = d.id
            GROUP BY d.id, d.batch_no, d.source_batch_id, b.batch_code, d.processed_date,
                     d.birds_processed, d.dressed_kg, d.notes
            ORDER BY d.id DESC
            """, (rs, row) -> new DressedBatch(rs.getLong("id"), rs.getString("batch_no"),
                rs.getLong("source_batch_id"), rs.getString("batch_code"), rs.getDate("processed_date").toLocalDate(),
                rs.getInt("birds_processed"), rs.getBigDecimal("dressed_kg"), rs.getBigDecimal("stock_kg"),
                rs.getString("notes")));
    }

    private DressedBatch get(Long id) {
        return jdbc.query("""
            SELECT d.id, d.batch_no, d.source_batch_id, b.batch_code, d.processed_date,
                   d.birds_processed, d.dressed_kg,
                   COALESCE((SELECT SUM(m.quantity_change) FROM dressed_stock_movements m
                             WHERE m.dressed_batch_id = d.id), 0) AS stock_kg, d.notes
            FROM dressed_chicken_batches d
            JOIN poultry_batches b ON b.id = d.source_batch_id
            WHERE d.id = ?
            """, (rs, row) -> new DressedBatch(rs.getLong("id"), rs.getString("batch_no"),
                rs.getLong("source_batch_id"), rs.getString("batch_code"), rs.getDate("processed_date").toLocalDate(),
                rs.getInt("birds_processed"), rs.getBigDecimal("dressed_kg"), rs.getBigDecimal("stock_kg"),
                rs.getString("notes")), id).stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Dressed batch not found"));
    }
}
