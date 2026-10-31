package com.poultry.customer;

import com.poultry.sale.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {
    public record CreateRequest(@NotBlank @Size(max = 120) String name, @Size(max = 40) String phone, @Size(max = 255) String address,
                                BigDecimal lat, BigDecimal lon, @PositiveOrZero BigDecimal creditLimit, @PositiveOrZero Integer termsDays) {}
    public record CreditRequest(@NotNull @PositiveOrZero BigDecimal creditLimit, @NotNull @PositiveOrZero Integer termsDays) {}
    public record PaymentRequest(@NotNull @Positive BigDecimal amount, @NotBlank String method, @Size(max = 80) String reference) {}
    public record Statement(CustomerService.CustomerView customer, List<SaleService.SaleView> invoices) {}

    private final CustomerService customers; private final SaleService sales; private final PaymentService payments;
    public CustomerController(CustomerService c, SaleService s, PaymentService p) { customers = c; sales = s; payments = p; }

    @GetMapping("/customers") public List<CustomerService.CustomerView> list() { return customers.list(); }

    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public CustomerService.CustomerView create(@Valid @RequestBody CreateRequest r) {
        return customers.create(new CustomerService.CreateCustomer(r.name(), r.phone(), r.address(), r.lat(), r.lon(), r.creditLimit(), r.termsDays()));
    }

    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping("/customers/{id}/credit")
    public CustomerService.CustomerView credit(@PathVariable Long id, @Valid @RequestBody CreditRequest r) { return customers.setCredit(id, r.creditLimit(), r.termsDays()); }

    @GetMapping("/customers/{id}/statement")
    public Statement statement(@PathVariable Long id) { return new Statement(customers.get(id), sales.list(id, 200, 0)); }

    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','PERM_PAY')")
    @PostMapping("/customers/{id}/payments") @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.Receipt pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r) {
        return payments.record(id, null, r.amount(), r.method(), r.reference());
    }
}
