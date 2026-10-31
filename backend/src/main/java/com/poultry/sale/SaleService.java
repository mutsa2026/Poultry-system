package com.poultry.sale;

import com.poultry.customer.CustomerService;
import com.poultry.repo.*;
import com.poultry.service.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    public record CreateSale(Long customerId, String product, Long batchId, Long dressedBatchId, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal discountPct, BigDecimal paidNow, String method) {}
    public record SaleView(Long id, String saleNo, Long customerId, String customerName, LocalDate saleDate, LocalDate dueDate,
                           String product, String batchCode, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct,
                           BigDecimal total, BigDecimal paid, BigDecimal balance, String status, String paymentStatus) {}

    static final Set<String> PRODUCTS = Set.of("LIVE_BROILER", "DRESSED_CHICKEN");
    static final Set<String> METHODS = Set.of("CASH", "BANK_TRANSFER", "MOBILE_MONEY");

    private final JdbcTemplate jdbc; private final CustomerService customers; private final BatchRepository batches;
    private final StockMovementRepository movements; private final AuditService audit; private final CurrentUser me;
    private final BusinessTime time; private final Perms perms;

    public SaleService(JdbcTemplate j, CustomerService c, BatchRepository b, StockMovementRepository m, AuditService a,
                       CurrentUser u, BusinessTime t, Perms p) { jdbc = j; customers = c; batches = b; movements = m; audit = a; me = u; time = t; perms = p; }

    private static final String SELECT = """
        SELECT s.id, s.sale_no, s.customer_id, c.name, s.sale_date, s.due_date, s.product,
               COALESCE(b.batch_code, db.batch_no) AS batch_code, s.quantity,
               s.unit_price, s.discount_pct, s.total,
               COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.sale_id = s.id), 0) AS paid, s.status
        FROM sales s JOIN customers c ON c.id = s.customer_id
        LEFT JOIN poultry_batches b ON b.id = s.batch_id
        LEFT JOIN dressed_chicken_batches db ON db.id = s.dressed_batch_id
        """;

    private SaleView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal total = rs.getBigDecimal("total"), paid = rs.getBigDecimal("paid"), bal = total.subtract(paid);
        String status = rs.getString("status");
        LocalDate due = rs.getDate("due_date").toLocalDate();
        String ps = "CANCELLED".equals(status) ? "CANCELLED" : bal.signum() == 0 ? "PAID"
            : due.isBefore(time.today()) ? "OVERDUE" : paid.signum() > 0 ? "PART_PAID" : "UNPAID";
        return new SaleView(rs.getLong("id"), rs.getString("sale_no"), rs.getLong("customer_id"), rs.getString("name"),
            rs.getDate("sale_date").toLocalDate(), due, rs.getString("product"), rs.getString("batch_code"),
            rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_pct"), total, paid, bal, status, ps);
    }

    @Transactional(readOnly = true)
    public List<SaleView> list(Long customerId, int limit, int offset) {
        return jdbc.query(SELECT + " WHERE (?::bigint IS NULL OR s.customer_id = ?) ORDER BY s.id DESC LIMIT ? OFFSET ?",
            (rs, i) -> map(rs), customerId, customerId, Math.min(limit, 200), offset);
    }

    @Transactional(readOnly = true)
    public SaleView get(Long id) {
        return jdbc.query(SELECT + " WHERE s.id = ?", (rs, i) -> map(rs), id).stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Sale not found"));
    }

    /** Validate the sale, deduct its selected stock lot once, enforce credit, and record any payment now. */
    @Transactional
    public SaleView create(CreateSale r) {
        if (!PRODUCTS.contains(r.product())) throw new BusinessException(HttpStatus.BAD_REQUEST, "Unknown product");
        if (r.quantity() == null || r.quantity().signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity must be above 0");
        if (r.unitPrice() == null || r.unitPrice().signum() < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Price can’t be negative");
        BigDecimal disc = r.discountPct() == null ? BigDecimal.ZERO : r.discountPct();
        if (disc.signum() < 0 || disc.compareTo(BigDecimal.valueOf(100)) > 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Discount must be 0 to 100");
        if (disc.signum() > 0 && !perms.can("PERM_DISCOUNT")) throw new BusinessException(HttpStatus.FORBIDDEN, "You aren’t allowed to give discounts");

        customers.lock(r.customerId());
        BigDecimal total = r.quantity().multiply(r.unitPrice()).multiply(BigDecimal.ONE.subtract(disc.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = r.paidNow() == null ? BigDecimal.ZERO : r.paidNow();
        if (paid.signum() < 0 || paid.compareTo(total) > 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Amount paid now must be between 0 and the total of " + total);
        if (paid.signum() > 0 && (r.method() == null || !METHODS.contains(r.method())))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose how the customer paid");
        if (paid.signum() > 0 && !perms.can("PERM_PAY")) throw new BusinessException(HttpStatus.FORBIDDEN, "You aren’t allowed to record payments");

        BigDecimal onCredit = total.subtract(paid);
        var cust = customers.get(r.customerId());
        if (onCredit.signum() > 0 && cust.balance().add(onCredit).compareTo(cust.creditLimit()) > 0)
            throw new BusinessException(HttpStatus.CONFLICT, cust.name() + " would owe $" + cust.balance().add(onCredit) + ", over their $" + cust.creditLimit() + " credit limit");

        Long userId = me.id();
        LocalDate today = time.today();
        if ("LIVE_BROILER".equals(r.product())) {
            if (r.dressedBatchId() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Live-bird sales can’t use a dressed-chicken batch");
            if (r.batchId() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose which batch the birds come from");
            if (r.quantity().stripTrailingZeros().scale() > 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Birds must be a whole number");
            batches.findByIdForUpdate(r.batchId()).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found"));
            long stock = movements.currentStock(r.batchId());
            if (r.quantity().longValue() > stock) throw new BusinessException(HttpStatus.CONFLICT, "Only " + stock + " birds are alive in that batch");
        } else {
            if (r.batchId() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Dressed-chicken sales can’t use a live-bird batch");
            if (r.dressedBatchId() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose which dressed-chicken batch to sell from");
            if (r.quantity().stripTrailingZeros().scale() > 3) throw new BusinessException(HttpStatus.BAD_REQUEST, "Dressed-chicken quantity can have at most 3 decimal places");
            List<String> selectedBatch = jdbc.query(
                "SELECT batch_no FROM dressed_chicken_batches WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getString("batch_no"), r.dressedBatchId());
            if (selectedBatch.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "Dressed-chicken batch not found");
            BigDecimal stock = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity_change), 0) FROM dressed_stock_movements WHERE dressed_batch_id = ?",
                BigDecimal.class, r.dressedBatchId());
            if (r.quantity().compareTo(stock) > 0)
                throw new BusinessException(HttpStatus.CONFLICT, "Only " + stock.stripTrailingZeros().toPlainString() + " kg remain in that dressed-chicken batch");
        }
        String saleNo = jdbc.queryForObject("SELECT 'INV-' || lpad(nextval('sale_no_seq')::text, 6, '0')", String.class);
        Long saleId = jdbc.queryForObject("""
            INSERT INTO sales(sale_no, customer_id, batch_id, dressed_batch_id, product, quantity, unit_price, discount_pct, total, sale_date, due_date, created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""", Long.class,
            saleNo, r.customerId(), "LIVE_BROILER".equals(r.product()) ? r.batchId() : null,
            "DRESSED_CHICKEN".equals(r.product()) ? r.dressedBatchId() : null, r.product(), r.quantity(), r.unitPrice(),
            disc, total, today, today.plusDays(cust.termsDays()), userId);
        if ("LIVE_BROILER".equals(r.product()))
            jdbc.update("""
                INSERT INTO stock_movements(batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
                VALUES (?, 'SALE', ?, 'sales', ?, ?, ?)""", r.batchId(), -r.quantity().intValue(), saleId, saleNo, userId);
        else
            jdbc.update("""
                INSERT INTO dressed_stock_movements(dressed_batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
                VALUES (?, 'SALE', ?, 'sales', ?, ?, ?)""", r.dressedBatchId(), r.quantity().negate(), saleId, saleNo, userId);
        if (paid.signum() > 0) insertPayment(saleId, paid, r.method(), null, userId);
        audit.record("SALE_CREATED", "sales", saleId, saleNo + " total=" + total + " paid=" + paid);
        return get(saleId);
    }

    @Transactional
    public SaleView cancel(Long id) {
        SaleView s = get(id);
        customers.lock(s.customerId());
        s = get(id);
        if (!"OPEN".equals(s.status())) throw new BusinessException(HttpStatus.CONFLICT, "Sale is already cancelled");
        if (s.paid().signum() > 0) throw new BusinessException(HttpStatus.CONFLICT, "This invoice has payments. Only unpaid invoices can be cancelled");
        if ("LIVE_BROILER".equals(s.product())) {
            Long batchId = jdbc.queryForObject("SELECT batch_id FROM sales WHERE id = ?", Long.class, id);
            batches.findByIdForUpdate(batchId);
            jdbc.update("""
                INSERT INTO stock_movements(batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
                VALUES (?, 'REVERSAL', ?, 'sales', ?, ?, ?)""", batchId, s.quantity().intValue(), id, "Cancelled " + s.saleNo(), me.id());
        } else if ("DRESSED_CHICKEN".equals(s.product())) {
            Long dressedBatchId = jdbc.queryForObject("SELECT dressed_batch_id FROM sales WHERE id = ?", Long.class, id);
            if (dressedBatchId != null) {
                jdbc.query("SELECT id FROM dressed_chicken_batches WHERE id = ? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), dressedBatchId);
                jdbc.update("""
                    INSERT INTO dressed_stock_movements(dressed_batch_id, movement_type, quantity_change, reference_type, reference_id, notes, created_by)
                    VALUES (?, 'REVERSAL', ?, 'sales', ?, ?, ?)""", dressedBatchId, s.quantity(), id, "Cancelled " + s.saleNo(), me.id());
            }
        }
        jdbc.update("UPDATE sales SET status = 'CANCELLED' WHERE id = ?", id);   // history is kept (FR-59)
        audit.record("SALE_CANCELLED", "sales", id, s.saleNo());
        return get(id);
    }

    String insertPayment(Long saleId, BigDecimal amount, String method, String reference, Long userId) {
        String receipt = jdbc.queryForObject("SELECT 'RCP-' || lpad(nextval('receipt_no_seq')::text, 6, '0')", String.class);
        jdbc.update("INSERT INTO payments(sale_id, receipt_no, amount, method, reference, received_by) VALUES (?,?,?,?,?,?)",
            saleId, receipt, amount, method, reference, userId);
        return receipt;
    }
}
