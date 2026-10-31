package com.poultry.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "poultry_batches")
public class PoultryBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_code", nullable = false, unique = true) private String batchCode;
    @Column(name = "supplier_id") private Long supplierId;
    @Column(name = "arrival_date", nullable = false) private LocalDate arrivalDate;
    private String breed;
    @Column(name = "initial_quantity", nullable = false) private int initialQuantity;
    @Column(name = "purchase_cost", nullable = false, precision = 14, scale = 2) private BigDecimal purchaseCost;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.ACTIVE;
    @Column(name = "created_by", nullable = false) private Long createdBy;

    public enum Status { ACTIVE, CLOSED }

    public Long getId() { return id; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String v) { batchCode = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { supplierId = v; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDate v) { arrivalDate = v; }
    public String getBreed() { return breed; }
    public void setBreed(String v) { breed = v; }
    public int getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(int v) { initialQuantity = v; }
    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(BigDecimal v) { purchaseCost = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { createdBy = v; }
}
