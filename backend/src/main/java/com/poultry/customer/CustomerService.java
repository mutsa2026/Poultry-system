package com.poultry.customer;

import com.poultry.service.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    public record CustomerView(Long id, String name, String phone, String address, BigDecimal lat, BigDecimal lon,
                               BigDecimal creditLimit, int termsDays, BigDecimal balance, Integer daysOverdue, String status) {}
    public record CreateCustomer(String name, String phone, String address, BigDecimal lat, BigDecimal lon,
                                 BigDecimal creditLimit, Integer termsDays) {}

    private final JdbcTemplate jdbc; private final BusinessTime time; private final AuditService audit; private final Perms perms;
    public CustomerService(JdbcTemplate j, BusinessTime t, AuditService a, Perms p) { jdbc = j; time = t; audit = a; perms = p; }

    private static final String SELECT = """
        SELECT c.id, c.name, c.phone, c.address, c.lat, c.lon, c.credit_limit, c.terms_days,
          COALESCE((SELECT SUM(s.total) FROM sales s WHERE s.customer_id = c.id AND s.status = 'OPEN'), 0)
          - COALESCE((SELECT SUM(p.amount) FROM payments p JOIN sales s ON s.id = p.sale_id
                      WHERE s.customer_id = c.id AND s.status = 'OPEN'), 0) AS balance,
          (SELECT MAX(?::date - s.due_date) FROM sales s
            WHERE s.customer_id = c.id AND s.status = 'OPEN' AND s.due_date < ?::date
              AND s.total > COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.sale_id = s.id), 0)) AS days_overdue
        FROM customers c
        """;

    private CustomerView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal bal = rs.getBigDecimal("balance"), lim = rs.getBigDecimal("credit_limit");
        Integer od = (Integer) rs.getObject("days_overdue");
        String status = bal.signum() == 0 ? "PAID_UP"
            : (od != null && od > 30) ? "OVERDUE"
            : (od != null && od > 0) ? "LATE"
            : (lim.signum() > 0 && bal.compareTo(lim.multiply(new BigDecimal("0.85"))) >= 0) ? "NEAR_LIMIT" : "CURRENT";
        return new CustomerView(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("address"),
            rs.getBigDecimal("lat"), rs.getBigDecimal("lon"), lim, rs.getInt("terms_days"), bal, od, status);
    }

    @Transactional(readOnly = true)
    public List<CustomerView> list() {
        return jdbc.query(SELECT + " WHERE c.active ORDER BY c.name", (rs, i) -> map(rs), time.today(), time.today());
    }

    @Transactional(readOnly = true)
    public CustomerView get(Long id) {
        return jdbc.query(SELECT + " WHERE c.id = ?", (rs, i) -> map(rs), time.today(), time.today(), id).stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    @Transactional
    public CustomerView create(CreateCustomer r) {
        // Only the owner may set credit limits (workers can't change credit limits).
        BigDecimal limit = perms.owner() && r.creditLimit() != null ? r.creditLimit() : BigDecimal.ZERO;
        int terms = perms.owner() && r.termsDays() != null ? r.termsDays() : 14;
        Long id = jdbc.queryForObject("""
            INSERT INTO customers(name, phone, address, lat, lon, credit_limit, terms_days)
            VALUES (?,?,?,?,?,?,?) RETURNING id""", Long.class, r.name(), r.phone(), r.address(), r.lat(), r.lon(), limit, terms);
        audit.record("CUSTOMER_CREATED", "customers", id, r.name());
        return get(id);
    }

    @Transactional
    public CustomerView setCredit(Long id, BigDecimal limit, int termsDays) {
        if (limit.signum() < 0 || termsDays < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Credit limit and terms can’t be negative");
        jdbc.update("UPDATE customers SET credit_limit = ?, terms_days = ? WHERE id = ?", limit, termsDays, id);
        audit.record("CREDIT_LIMIT_CHANGED", "customers", id, "limit=" + limit + " terms=" + termsDays);
        return get(id);
    }

    /** Row-locks the customer so concurrent sales and payments for one customer run one at a time. */
    public void lock(Long id) {
        List<Long> r = jdbc.queryForList("SELECT id FROM customers WHERE id = ? AND active FOR UPDATE", Long.class, id);
        if (r.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "Customer not found");
    }

    public BigDecimal balance(Long id) {
        return jdbc.queryForObject("""
            SELECT COALESCE((SELECT SUM(total) FROM sales WHERE customer_id = ? AND status = 'OPEN'), 0)
                 - COALESCE((SELECT SUM(p.amount) FROM payments p JOIN sales s ON s.id = p.sale_id
                             WHERE s.customer_id = ? AND s.status = 'OPEN'), 0)""", BigDecimal.class, id, id);
    }
}
