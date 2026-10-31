package com.poultry.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "stock_movements")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false) private Type type;
    @Column(name = "quantity_change", nullable = false) private int quantityChange;
    @Column(name = "reference_type") private String referenceType;
    @Column(name = "reference_id") private Long referenceId;
    private String notes;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

    public enum Type { OPENING, SALE, MORTALITY, CULL, MISSING, PROCESSING, ADJUSTMENT, REVERSAL }

    public static StockMovement of(Long batchId, Type type, int change, String notes, Long userId) {
        StockMovement m = new StockMovement();
        m.batchId = batchId; m.type = type; m.quantityChange = change; m.notes = notes; m.createdBy = userId;
        return m;
    }
    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public Type getType() { return type; }
    public int getQuantityChange() { return quantityChange; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
