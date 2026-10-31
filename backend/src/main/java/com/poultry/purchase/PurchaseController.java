package com.poultry.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PurchaseController {
    public record SupplierRequest(@NotBlank @Size(max = 120) String name, @Size(max = 40) String phone, @Size(max = 255) String address) {}
    public record PurchaseRequest(@NotNull Long supplierId, @NotBlank String item, @Size(max = 255) String description, @NotNull @Positive BigDecimal quantity,
                                  @NotNull @Positive BigDecimal totalCost, @PositiveOrZero BigDecimal paidNow, @PositiveOrZero Integer dueInDays) {}
    public record PayRequest(@NotNull @Positive BigDecimal amount) {}
    public record ExpenseRequest(@NotBlank @Size(max = 40) String category, @Size(max = 255) String description, @NotNull @Positive BigDecimal amount, LocalDate date, Long batchId) {}

    private final PurchaseService svc;
    public PurchaseController(PurchaseService s) { svc = s; }

    @PreAuthorize("hasRole('OWNER')") @GetMapping("/suppliers")
    public List<PurchaseService.SupplierView> suppliers() { return svc.suppliers(); }

    @PreAuthorize("hasRole('OWNER')") @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public PurchaseService.SupplierView createSupplier(@Valid @RequestBody SupplierRequest r) { return svc.createSupplier(r.name(), r.phone(), r.address()); }

    @PreAuthorize("hasRole('OWNER')") @PostMapping("/purchases") @ResponseStatus(HttpStatus.CREATED)
    public PurchaseService.SupplierView purchase(@Valid @RequestBody PurchaseRequest r) {
        return svc.purchase(new PurchaseService.CreatePurchase(r.supplierId(), r.item(), r.description(), r.quantity(), r.totalCost(), r.paidNow(), r.dueInDays(), null));
    }

    @PreAuthorize("hasRole('OWNER')") @PostMapping("/suppliers/{id}/payments")
    public PurchaseService.SupplierView pay(@PathVariable Long id, @Valid @RequestBody PayRequest r) { return svc.pay(id, r.amount()); }

    @PreAuthorize("hasRole('OWNER')") @GetMapping("/expenses")
    public List<PurchaseService.ExpenseView> expenses(@RequestParam(defaultValue = "50") int limit) { return svc.expenses(limit); }

    @PreAuthorize("hasRole('OWNER')") @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED)
    public PurchaseService.ExpenseView expense(@Valid @RequestBody ExpenseRequest r) {
        return svc.expense(new PurchaseService.CreateExpense(r.category(), r.description(), r.amount(), r.date(), r.batchId()));
    }
}
