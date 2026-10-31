package com.poultry.report;

import com.poultry.customer.CustomerService;
import com.poultry.service.BusinessTime;
import java.math.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Data behind the Analytics page. Money figures, so the controller limits it to the owner or anyone allowed to see profit. */
@Service
public class AnalyticsService {
    public record Week(LocalDate start, BigDecimal sales, BigDecimal received) {}
    public record BatchRate(String batch, int initial, long deaths, BigDecimal ratePct) {}
    public record Amount(String label, BigDecimal amount) {}
    public record Analytics(List<Week> weekly, List<BatchRate> mortality, List<ReportService.DayCount> deathsPerDay,
                            List<Amount> expensesByCategory, List<Amount> salesByProduct, List<Amount> topDebtors,
                            List<ReportService.AgeBucket> ageing) {}

    private final JdbcTemplate jdbc; private final BusinessTime time; private final CustomerService customers; private final ReportService reports;
    public AnalyticsService(JdbcTemplate j, BusinessTime t, CustomerService c, ReportService r) { jdbc = j; time = t; customers = c; reports = r; }

    @Transactional(readOnly = true)
    public Analytics build() {
        LocalDate today = time.today();
        List<Week> weekly = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {                       // eight weeks, each ending 7*i days before today
            LocalDate start = today.minusDays(6L + 7L * i), end = start.plusDays(6);
            BigDecimal sales = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM sales WHERE status = 'OPEN' AND sale_date BETWEEN ? AND ?", BigDecimal.class, start, end);
            BigDecimal got = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN sales s ON s.id = p.sale_id
                WHERE s.status = 'OPEN' AND p.created_at >= ? AND p.created_at < ?""", BigDecimal.class,
                Timestamp.from(time.startOfDay(start)), Timestamp.from(time.startOfDay(end.plusDays(1))));
            weekly.add(new Week(start, sales, got));
        }
        List<BatchRate> mortality = jdbc.query("""
            SELECT b.batch_code, b.initial_quantity, COALESCE(SUM(m.quantity), 0)
            FROM poultry_batches b LEFT JOIN mortality_events m ON m.batch_id = b.id GROUP BY b.id ORDER BY b.id""",
            (rs, i) -> { long d = rs.getLong(3); int init = rs.getInt(2);
                return new BatchRate(rs.getString(1), init, d, BigDecimal.valueOf(d * 100L).divide(BigDecimal.valueOf(init), 2, RoundingMode.HALF_UP)); });

        LocalDate from30 = today.minusDays(29);
        Map<LocalDate, Integer> m = new HashMap<>();
        jdbc.query("SELECT event_date, SUM(quantity) FROM mortality_events WHERE event_date >= ? GROUP BY event_date",
            rs -> { m.put(rs.getDate(1).toLocalDate(), rs.getInt(2)); }, from30);
        List<ReportService.DayCount> deaths = new ArrayList<>();
        for (int i = 0; i < 30; i++) { LocalDate d = from30.plusDays(i); deaths.add(new ReportService.DayCount(d, m.getOrDefault(d, 0))); }

        List<Amount> exp = jdbc.query("SELECT category, SUM(amount) FROM expenses WHERE expense_date >= ? GROUP BY category ORDER BY 2 DESC",
            (rs, i) -> new Amount(rs.getString(1), rs.getBigDecimal(2)), from30);
        List<Amount> byProduct = jdbc.query("SELECT product, SUM(total) FROM sales WHERE status = 'OPEN' AND sale_date >= ? GROUP BY product ORDER BY 2 DESC",
            (rs, i) -> new Amount(rs.getString(1).equals("LIVE_BROILER") ? "Live broilers" : "Dressed chicken", rs.getBigDecimal(2)), from30);
        List<Amount> debtors = customers.list().stream().filter(c -> c.balance().signum() > 0)
            .sorted((a, b) -> b.balance().compareTo(a.balance())).limit(5).map(c -> new Amount(c.name(), c.balance())).toList();
        return new Analytics(weekly, mortality, deaths, exp, byProduct, debtors, reports.ageing());
    }
}
