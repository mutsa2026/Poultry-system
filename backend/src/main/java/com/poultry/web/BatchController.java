package com.poultry.web;

import com.poultry.domain.*;
import com.poultry.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    private final BatchService batches;
    private final MortalityService mortality;
    public BatchController(BatchService b, MortalityService m) { batches = b; mortality = m; }

    public record RegisterRequest(
        @NotBlank @Size(max = 40) String batchCode, Long supplierId, @NotNull LocalDate arrivalDate,
        @Size(max = 60) String breed, @Positive int initialQuantity, @NotNull @PositiveOrZero BigDecimal purchaseCost) {}

    public record MortalityRequest(
        @NotNull LocalDate eventDate, @Positive int quantity, @Size(max = 120) String cause, @Size(max = 255) String notes) {}

    public record MortalityResponse(Long id, Long batchId, LocalDate eventDate, int quantity, String cause) {}

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @GetMapping public List<BatchService.BatchView> list() { return batches.list(); }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BatchService.BatchView register(@Valid @RequestBody RegisterRequest r) {
        return batches.register(new BatchService.RegisterBatch(
            r.batchCode(), r.supplierId(), r.arrivalDate(), r.breed(), r.initialQuantity(), r.purchaseCost()));
    }

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @GetMapping("/{id}") public BatchService.BatchView get(@PathVariable Long id) { return batches.get(id); }

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @GetMapping("/{id}/movements")
    public List<StockMovement> movements(@PathVariable Long id) { return batches.history(id); }

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @PostMapping("/{id}/mortality") @ResponseStatus(HttpStatus.CREATED)
    public MortalityResponse reportMortality(@PathVariable Long id, @Valid @RequestBody MortalityRequest r) {
        MortalityEvent e = mortality.report(id, new MortalityService.ReportDeaths(
            r.eventDate(), r.quantity(), r.cause(), r.notes()));
        return new MortalityResponse(e.getId(), e.getBatchId(), e.getEventDate(), e.getQuantity(), e.getCause());
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/{id}/mortality-rate")
    public MortalityService.MortalityReport rate(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mortality.report(id, from, to);
    }
}
