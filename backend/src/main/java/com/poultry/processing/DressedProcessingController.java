package com.poultry.processing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DressedProcessingController {
    public record ProcessRequest(@NotNull Long sourceBatchId, @NotNull LocalDate processedDate,
            @Positive int birdsProcessed, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal dressedKg,
            @Size(max = 255) String notes) {}

    private final DressedProcessingService processing;

    public DressedProcessingController(DressedProcessingService processing) {
        this.processing = processing;
    }

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @PostMapping("/processing") @ResponseStatus(HttpStatus.CREATED)
    public DressedProcessingService.DressedBatch process(@Valid @RequestBody ProcessRequest request) {
        return processing.process(new DressedProcessingService.ProcessBatch(request.sourceBatchId(), request.processedDate(),
            request.birdsProcessed(), request.dressedKg(), request.notes()));
    }

    @PreAuthorize("hasAnyRole('OWNER','WORKER')")
    @GetMapping("/dressed-batches")
    public List<DressedProcessingService.DressedBatch> list() {
        return processing.list();
    }
}
