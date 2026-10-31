package com.poultry.report;

import com.poultry.customer.CustomerService;
import com.poultry.purchase.PurchaseService;
import com.poultry.service.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-89: CSV exports. Text cells that start with = + - @ are prefixed so spreadsheets can't run them as formulas. */
@Service
public class ExportService {
    public static final Set<String> NAMES = Set.of("sales", "payments", "customers", "expenses", "suppliers", "mortality", "stock-movements");
    private final JdbcTemplate jdbc; private final BusinessTime time; private final Perms perms;
    private final CustomerService customers; private final PurchaseService purchases; private final AuditService audit;

    public ExportService(JdbcTemplate j, BusinessTime t, Perms p, CustomerService c, PurchaseService pu, AuditService a) { jdbc = j; time = t; perms = p; customers = c; purchases = pu; audit = a; }

    private static String cell(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        if (!(o instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
    private static void row(StringBuilder sb, Object... v) {
        for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(','); sb.append(cell(v[i])); }
        sb.append("\r\n");
    }

    @Transactional
    public String csv(String name, LocalDate from, LocalDate to) {
        if (!NAMES.contains(name)) throw new BusinessException(HttpStatus.NOT_FOUND, "Unknown export");
        boolean owner = perms.owner();
        boolean money = perms.can("PERM_PROFIT");
        if (Set.of("sales", "payments", "customers").contains(name) && !money) throw new BusinessException(HttpStatus.FORBIDDEN, "You aren’t allowed to export financial records");
        if (Set.of("expenses", "suppliers").contains(name) && !owner) throw new BusinessException(HttpStatus.FORBIDDEN, "Only the owner can export this");
        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM so Excel reads accents correctly
        String zone = time.zoneId();
        switch (name) {
            case "sales" -> {
                row(sb, "Invoice", "Date", "Due", "Customer", "Product", "Batch", "Quantity", "Unit price", "Discount %", "Total", "Paid", "Balance", "Status");
                jdbc.query("""
                    SELECT s.sale_no, s.sale_date, s.due_date, c.name, s.product, b.batch_code, s.quantity, s.unit_price, s.discount_pct, s.total,
                           COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.sale_id = s.id), 0), s.status
                    FROM sales s JOIN customers c ON c.id = s.customer_id LEFT JOIN poultry_batches b ON b.id = s.batch_id
                    WHERE (?::date IS NULL OR s.sale_date >= ?::date) AND (?::date IS NULL OR s.sale_date <= ?::date) ORDER BY s.id""",
                    rs -> { var total = rs.getBigDecimal(10); var paid = rs.getBigDecimal(11);
                        row(sb, rs.getString(1), rs.getDate(2), rs.getDate(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), total, paid, total.subtract(paid), rs.getString(12)); },
                    from, from, to, to);
            }
            case "payments" -> {
                row(sb, "Receipt", "Date", "Invoice", "Customer", "Amount", "Method", "Reference", "Received by");
                jdbc.query("""
                    SELECT p.receipt_no, (p.created_at AT TIME ZONE ?)::date, s.sale_no, c.name, p.amount, p.method, p.reference, u.username
                    FROM payments p JOIN sales s ON s.id = p.sale_id JOIN customers c ON c.id = s.customer_id JOIN users u ON u.id = p.received_by
                    WHERE (?::date IS NULL OR (p.created_at AT TIME ZONE ?)::date >= ?::date) AND (?::date IS NULL OR (p.created_at AT TIME ZONE ?)::date <= ?::date)
                    ORDER BY p.id""",
                    rs -> { row(sb, rs.getString(1), rs.getDate(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7), rs.getString(8)); },
                    zone, from, zone, from, to, zone, to);
            }
            case "customers" -> {
                row(sb, "Customer", "Phone", "Address", "Credit limit", "Terms (days)", "Balance owed", "Days overdue", "Status");
                for (var c : customers.list()) row(sb, c.name(), c.phone(), c.address(), c.creditLimit(), c.termsDays(), c.balance(), c.daysOverdue(), c.status());
            }
            case "expenses" -> {
                row(sb, "Date", "Category", "Description", "Amount", "Batch");
                for (var e : purchases.expenses(100000)) if ((from == null || !e.date().isBefore(from)) && (to == null || !e.date().isAfter(to))) row(sb, e.date(), e.category(), e.description(), e.amount(), e.batchCode());
            }
            case "suppliers" -> {
                row(sb, "Supplier", "Phone", "Balance owed", "Next due", "Days late");
                for (var s : purchases.suppliers()) row(sb, s.name(), s.phone(), s.balance(), s.nextDue(), s.daysLate());
            }
            case "mortality" -> {
                row(sb, "Date", "Batch", "Birds died", "Cause", "Reported by");
                jdbc.query("""
                    SELECT e.event_date, b.batch_code, e.quantity, e.cause, u.username
                    FROM mortality_events e JOIN poultry_batches b ON b.id = e.batch_id JOIN users u ON u.id = e.reported_by
                    WHERE (?::date IS NULL OR e.event_date >= ?::date) AND (?::date IS NULL OR e.event_date <= ?::date) ORDER BY e.event_date, e.id""",
                    rs -> { row(sb, rs.getDate(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)); }, from, from, to, to);
            }
            default -> {
                row(sb, "Time", "Batch", "Type", "Change", "Note", "By");
                jdbc.query("""
                    SELECT (m.created_at AT TIME ZONE ?)::timestamp, b.batch_code, m.movement_type, m.quantity_change, m.notes, u.username
                    FROM stock_movements m JOIN poultry_batches b ON b.id = m.batch_id JOIN users u ON u.id = m.created_by
                    WHERE (?::date IS NULL OR (m.created_at AT TIME ZONE ?)::date >= ?::date) AND (?::date IS NULL OR (m.created_at AT TIME ZONE ?)::date <= ?::date)
                    ORDER BY m.id""",
                    rs -> { row(sb, rs.getTimestamp(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6)); },
                    zone, from, zone, from, to, zone, to);
            }
        }
        audit.record("EXPORT", "reports", null, name + (from != null ? " from " + from : "") + (to != null ? " to " + to : ""));
        return sb.toString();
    }
}
