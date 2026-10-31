package com.poultry.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "mortality_events")
public class MortalityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    @Column(nullable = false) private int quantity;
    private String cause;
    private String notes;
    @Column(name = "movement_id", nullable = false, unique = true) private Long movementId;
    @Column(name = "reported_by", nullable = false) private Long reportedBy;

    public static MortalityEvent of(Long batchId, LocalDate date, int qty, String cause, String notes, Long movementId, Long user) {
        MortalityEvent e = new MortalityEvent();
        e.batchId = batchId; e.eventDate = date; e.quantity = qty; e.cause = cause; e.notes = notes;
        e.movementId = movementId; e.reportedBy = user;
        return e;
    }
    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public LocalDate getEventDate() { return eventDate; }
    public int getQuantity() { return quantity; }
    public String getCause() { return cause; }
}
