package com.poultry.sale;

import com.poultry.customer.CustomerService;
import com.poultry.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public record Allocation(String saleNo, BigDecimal amount) {}
    public record Receipt(String receiptNo, BigDecimal amount, String method, BigDecimal newBalance, List<Allocation> allocations) {}
    private record Open(Long id, String saleNo, BigDecimal outstanding) {}

    private final JdbcTemplate jdbc; private final CustomerService customers; private final SaleService sales;
    private final AuditService audit; private final CurrentUser me;
    public PaymentService(JdbcTemplate j, CustomerService c, SaleService s, AuditService a, CurrentUser u) { jdbc = j; customers = c; sales = s; audit = a; me = u; }

    /** FR-60/65: full or partial payment, allocated oldest-due-first across the customer's open invoices (or just one). */
    @Transactional
    public Receipt record(Long customerId, Long onlySaleId, BigDecimal amount, String method, String reference) {
        if (amount == null || amount.signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        if (!SaleService.METHODS.contains(method)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose cash, bank transfer or mobile money");
        customers.lock(customerId);

        List<Open> open = jdbc.query("""
            SELECT s.id, s.sale_no, s.total - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.sale_id = s.id), 0)
            FROM sales s WHERE s.customer_id = ? AND s.status = 'OPEN' AND (?::bigint IS NULL OR s.id = ?)
            ORDER BY s.due_date, s.id""", (rs, i) -> new Open(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3)), customerId, onlySaleId, onlySaleId)
            .stream().filter(o -> o.outstanding().signum() > 0).toList();
        BigDecimal owed = open.stream().map(Open::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(owed) > 0) throw new BusinessException(HttpStatus.CONFLICT, "The customer owes $" + owed + ", so a payment of $" + amount + " is too much");

        Long userId = me.id();
        String receipt = jdbc.queryForObject("SELECT 'RCP-' || lpad(nextval('receipt_no_seq')::text, 6, '0')", String.class);
        BigDecimal left = amount; List<Allocation> alloc = new ArrayList<>();
        for (Open o : open) {
            if (left.signum() == 0) break;
            BigDecimal part = left.min(o.outstanding());
            jdbc.update("INSERT INTO payments(sale_id, receipt_no, amount, method, reference, received_by) VALUES (?,?,?,?,?,?)",
                o.id(), receipt, part, method, reference, userId);
            alloc.add(new Allocation(o.saleNo(), part));
            left = left.subtract(part);
        }
        audit.record("PAYMENT_RECORDED", "customers", customerId, receipt + " amount=" + amount + " method=" + method);
        return new Receipt(receipt, amount, method, customers.balance(customerId), alloc);
    }

    public Receipt forSale(Long saleId, BigDecimal amount, String method, String reference) {
        return record(sales.get(saleId).customerId(), saleId, amount, method, reference);
    }
}
