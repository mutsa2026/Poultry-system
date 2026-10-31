package com.poultry.purchase;

import com.poultry.feed.FeedService;
import com.poultry.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {
    public record SupplierView(Long id, String name, String phone, BigDecimal balance, LocalDate nextDue, Integer daysLate) {}
    public record CreatePurchase(Long supplierId, String item, String description, BigDecimal quantity, BigDecimal totalCost,
                                 BigDecimal paidNow, Integer dueInDays, Long batchId) {}
    public record CreateExpense(String category, String description, BigDecimal amount, LocalDate date, Long batchId) {}
    public record ExpenseView(Long id, String category, String description, BigDecimal amount, LocalDate date, String batchCode) {}

    static final Set<String> ITEMS = Set.of("CHICKS", "FEED", "MEDICINE", "PACKAGING", "OTHER");
    private final JdbcTemplate jdbc; private final FeedService feed; private final AuditService audit; private final CurrentUser me; private final BusinessTime time;
    public PurchaseService(JdbcTemplate j, FeedService f, AuditService a, CurrentUser m, BusinessTime t) { jdbc = j; feed = f; audit = a; me = m; time = t; }

    /** Oldest bills are treated as paid first; the first one still open sets the due date. */
    @Transactional(readOnly = true)
    public List<SupplierView> suppliers() {
        record Row(Long id, String name, String phone, BigDecimal paid) {}
        List<Row> rows = jdbc.query("""
            SELECT s.id, s.name, s.phone, COALESCE((SELECT SUM(amount) FROM supplier_payments WHERE supplier_id = s.id), 0)
            FROM suppliers s WHERE s.active ORDER BY s.name""", (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)));
        List<SupplierView> out = new ArrayList<>();
        for (Row r : rows) {
            BigDecimal left = r.paid(), balance = BigDecimal.ZERO; LocalDate due = null;
            for (var p : jdbc.query("SELECT total_cost, due_date FROM purchases WHERE supplier_id = ? ORDER BY due_date, id",
                    (rs, i) -> Map.entry(rs.getBigDecimal(1), rs.getDate(2).toLocalDate()), r.id())) {
                BigDecimal cost = p.getKey();
                if (left.compareTo(cost) >= 0) { left = left.subtract(cost); continue; }
                BigDecimal open = cost.subtract(left); left = BigDecimal.ZERO;
                balance = balance.add(open);
                if (due == null) due = p.getValue();
            }
            Integer late = due != null && due.isBefore(time.today()) ? (int) java.time.temporal.ChronoUnit.DAYS.between(due, time.today()) : null;
            out.add(new SupplierView(r.id(), r.name(), r.phone(), balance, due, late));
        }
        return out;
    }

    @Transactional
    public SupplierView createSupplier(String name, String phone, String address) {
        Long id = jdbc.queryForObject("INSERT INTO suppliers(name, phone, address) VALUES (?,?,?) RETURNING id", Long.class, name, phone, address);
        audit.record("SUPPLIER_CREATED", "suppliers", id, name);
        return suppliers().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public SupplierView purchase(CreatePurchase r) {
        if (!ITEMS.contains(r.item())) throw new BusinessException(HttpStatus.BAD_REQUEST, "Unknown item");
        if (r.quantity() == null || r.quantity().signum() <= 0 || r.totalCost() == null || r.totalCost().signum() <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity and cost must be above 0");
        BigDecimal paid = r.paidNow() == null ? BigDecimal.ZERO : r.paidNow();
        if (paid.signum() < 0 || paid.compareTo(r.totalCost()) > 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Paid now must be between 0 and the total cost");
        jdbc.queryForList("SELECT id FROM suppliers WHERE id = ? AND active FOR UPDATE", Long.class, r.supplierId())
            .stream().findFirst().orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Supplier not found"));
        LocalDate today = time.today();
        Long id = jdbc.queryForObject("""
            INSERT INTO purchases(supplier_id, item, description, quantity, total_cost, purchase_date, due_date, created_by)
            VALUES (?,?,?,?,?,?,?,?) RETURNING id""", Long.class, r.supplierId(), r.item(), r.description(), r.quantity(), r.totalCost(),
            today, today.plusDays(r.dueInDays() == null ? 14 : r.dueInDays()), me.id());
        if ("FEED".equals(r.item())) feed.receive(feed.defaultFeedType(), r.quantity(), "Purchase " + id);   // FR-74
        if (paid.signum() > 0) jdbc.update("INSERT INTO supplier_payments(supplier_id, amount, paid_date, created_by) VALUES (?,?,?,?)", r.supplierId(), paid, today, me.id());
        audit.record("PURCHASE_POSTED", "purchases", id, r.item() + " cost=" + r.totalCost() + " paid=" + paid);
        return suppliers().stream().filter(s -> s.id().equals(r.supplierId())).findFirst().orElseThrow();
    }

    @Transactional
    public SupplierView pay(Long supplierId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        jdbc.queryForList("SELECT id FROM suppliers WHERE id = ? FOR UPDATE", Long.class, supplierId).stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Supplier not found"));
        SupplierView s = suppliers().stream().filter(x -> x.id().equals(supplierId)).findFirst().orElseThrow();
        if (amount.compareTo(s.balance()) > 0) throw new BusinessException(HttpStatus.CONFLICT, "You only owe " + s.name() + " $" + s.balance());
        jdbc.update("INSERT INTO supplier_payments(supplier_id, amount, paid_date, created_by) VALUES (?,?,?,?)", supplierId, amount, time.today(), me.id());
        audit.record("SUPPLIER_PAID", "suppliers", supplierId, "amount=" + amount);
        return suppliers().stream().filter(x -> x.id().equals(supplierId)).findFirst().orElseThrow();
    }

    @Transactional
    public ExpenseView expense(CreateExpense r) {
        if (r.amount() == null || r.amount().signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        Long id = jdbc.queryForObject("INSERT INTO expenses(category, description, amount, expense_date, batch_id, created_by) VALUES (?,?,?,?,?,?) RETURNING id",
            Long.class, r.category(), r.description(), r.amount(), r.date() == null ? time.today() : r.date(), r.batchId(), me.id());
        audit.record("EXPENSE_RECORDED", "expenses", id, r.category() + " " + r.amount());
        return expenses(50).stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> expenses(int limit) {
        return jdbc.query("""
            SELECT e.id, e.category, e.description, e.amount, e.expense_date, b.batch_code
            FROM expenses e LEFT JOIN poultry_batches b ON b.id = e.batch_id ORDER BY e.id DESC LIMIT ?""",
            (rs, i) -> new ExpenseView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getDate(5).toLocalDate(), rs.getString(6)), Math.min(limit, 200));
    }
}
