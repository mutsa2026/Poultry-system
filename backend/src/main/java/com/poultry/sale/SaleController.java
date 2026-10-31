package com.poultry.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SaleController {
    public record CreateRequest(@NotNull Long customerId, @NotBlank String product, Long batchId, Long dressedBatchId, @NotNull @Positive BigDecimal quantity,
                                @NotNull @PositiveOrZero BigDecimal unitPrice, @PositiveOrZero BigDecimal discountPct,
                                @PositiveOrZero BigDecimal paidNow, String method) {}
    public record PayRequest(@NotNull @Positive BigDecimal amount, @NotBlank String method, @Size(max = 80) String reference) {}

    private final SaleService sales; private final PaymentService payments;
    public SaleController(SaleService s, PaymentService p) { sales = s; payments = p; }

    @GetMapping("/sales")
    public List<SaleService.SaleView> list(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return sales.list(null, limit, offset); }

    @GetMapping("/sales/{id}") public SaleService.SaleView get(@PathVariable Long id) { return sales.get(id); }

    @PostMapping("/sales") @ResponseStatus(HttpStatus.CREATED)
    public SaleService.SaleView create(@Valid @RequestBody CreateRequest r) {
        return sales.create(new SaleService.CreateSale(r.customerId(), r.product(), r.batchId(), r.dressedBatchId(),
            r.quantity(), r.unitPrice(), r.discountPct(), r.paidNow(), r.method()));
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/sales/{id}/cancel") public SaleService.SaleView cancel(@PathVariable Long id) { return sales.cancel(id); }

    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','PERM_PAY')")
    @PostMapping("/sales/{id}/payments") @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.Receipt pay(@PathVariable Long id, @Valid @RequestBody PayRequest r) { return payments.forSale(id, r.amount(), r.method(), r.reference()); }
}
