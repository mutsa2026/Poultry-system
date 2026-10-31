package com.poultry.report;

import com.poultry.customer.CustomerService;
import com.poultry.feed.FeedService;
import com.poultry.health.HealthService;
import com.poultry.purchase.PurchaseService;
import com.poultry.service.*;
import java.math.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    public record Alert(String severity, String message) {}
    public record Dashboard(long liveBirds, BigDecimal salesThisWeek, BigDecimal receivedThisWeek, BigDecimal owedByCustomers,
                            BigDecimal owedToSuppliers, List<FeedService.FeedStock> feed, List<Alert> alerts, boolean showMoney) {}
    public record DayCount(LocalDate date, int deaths) {}
    public record Advice(String area, String level, String title, String text) {}
    public record Climate(int dayOfLife, int targetTempC, List<Advice> advice) {}
    public record Profit(String batchCode, BigDecimal revenue, BigDecimal birdCost, BigDecimal expenses, BigDecimal profit,
                         long birdsAlive, BigDecimal breakEvenPerBird, boolean estimated) {}
    public record AgeBucket(String bucket, BigDecimal amount) {}

    private final JdbcTemplate jdbc; private final BusinessTime time; private final Perms perms; private final CustomerService customers;
    private final PurchaseService purchases; private final FeedService feed; private final HealthService health;

    public ReportService(JdbcTemplate j, BusinessTime t, Perms p, CustomerService c, PurchaseService pu, FeedService f, HealthService h) {
        jdbc = j; time = t; perms = p; customers = c; purchases = pu; feed = f; health = h;
    }

    @Transactional(readOnly = true)
    public List<DayCount> mortalityDaily(Long batchId, int days) {
        days = Math.max(1, Math.min(days, 90));
        LocalDate from = time.today().minusDays(days - 1L);
        Map<LocalDate, Integer> m = new HashMap<>();
        jdbc.query("SELECT event_date, SUM(quantity) FROM mortality_events WHERE batch_id = ? AND event_date >= ? GROUP BY event_date",
            rs -> { m.put(rs.getDate(1).toLocalDate(), rs.getInt(2)); }, batchId, from);
        List<DayCount> out = new ArrayList<>();
        for (int i = 0; i < days; i++) { LocalDate d = from.plusDays(i); out.add(new DayCount(d, m.getOrDefault(d, 0))); }
        return out;
    }

    /** A spike is either of the last two days at or above 2.5× the usual daily deaths (and at least 5). */
    static boolean isSpike(List<DayCount> d) {
        if (d.size() < 5) return false;
        double base = d.subList(0, d.size() - 2).stream().mapToInt(DayCount::deaths).average().orElse(0);
        double limit = Math.max(5, 2.5 * base);
        return d.get(d.size() - 1).deaths() >= limit || d.get(d.size() - 2).deaths() >= limit;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        boolean money = perms.can("PERM_PROFIT");
        LocalDate weekStart = time.today().minusDays(6);
        long live = jdbc.queryForObject("SELECT COALESCE(SUM(m.quantity_change),0) FROM stock_movements m JOIN poultry_batches b ON b.id = m.batch_id WHERE b.status = 'ACTIVE'", Long.class);
        List<Alert> alerts = new ArrayList<>();

        jdbc.query("SELECT id, batch_code FROM poultry_batches WHERE status = 'ACTIVE' ORDER BY id", rs -> {
            Long id = rs.getLong(1); String code = rs.getString(2);
            List<DayCount> d = mortalityDaily(id, 14);
            if (isSpike(d)) alerts.add(new Alert("HIGH", code + " lost " + (d.get(12).deaths() + d.get(13).deaths()) + " birds in 2 days. That is well above usual."));
            for (var v : health.schedule(id)) {
                if (v.status().equals("OVERDUE")) alerts.add(new Alert("MEDIUM", code + " is overdue for " + v.vaccine() + "."));
                else if (v.status().equals("DUE")) alerts.add(new Alert("MEDIUM", code + " is due for " + v.vaccine() + " today."));
            }
        });
        var stock = feed.stock();
        stock.stream().filter(FeedService.FeedStock::low).forEach(f -> alerts.add(new Alert("MEDIUM", f.name() + " is down to " + f.onHand().stripTrailingZeros().toPlainString() + ". Reorder level is " + f.reorderLevel().stripTrailingZeros().toPlainString() + ".")));
        var since = Timestamp.from(time.startOfDay(time.today()));
        health.visitors(5).stream().filter(v -> v.risk() && !Timestamp.from(v.at()).before(since))
            .forEach(v -> alerts.add(new Alert("HIGH", "Biosecurity risk: " + v.name() + (v.poultryContact() ? " visited after poultry contact." : " visited without a footbath."))));

        BigDecimal sales = null, received = null, owedC = null, owedS = null;
        if (money) {
            sales = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM sales WHERE status = 'OPEN' AND sale_date >= ?", BigDecimal.class, weekStart);
            received = jdbc.queryForObject("SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN sales s ON s.id = p.sale_id WHERE s.status = 'OPEN' AND p.created_at >= ?",
                BigDecimal.class, Timestamp.from(time.startOfDay(weekStart)));
            var cs = customers.list();
            owedC = cs.stream().map(CustomerService.CustomerView::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            cs.stream().filter(c -> c.status().equals("OVERDUE")).forEach(c -> alerts.add(new Alert("HIGH", c.name() + " owes $" + c.balance() + " and is " + c.daysOverdue() + " days overdue.")));
            cs.stream().filter(c -> c.status().equals("NEAR_LIMIT")).forEach(c -> alerts.add(new Alert("MEDIUM", c.name() + " is close to their credit limit.")));
            if (perms.owner()) {
                var ss = purchases.suppliers();
                owedS = ss.stream().map(PurchaseService.SupplierView::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
                ss.stream().filter(s -> s.daysLate() != null).forEach(s -> alerts.add(new Alert("HIGH", "You owe " + s.name() + " $" + s.balance() + " and it is " + s.daysLate() + " days late.")));
            }
        }
        alerts.sort(Comparator.comparing(a -> a.severity().equals("HIGH") ? 0 : 1));
        return new Dashboard(live, sales, received, owedC, owedS, stock, alerts, money);
    }

    static int targetTemp(int day) { return day <= 7 ? 32 : day <= 14 ? 29 : day <= 21 ? 26 : day <= 28 ? 24 : 21; }

    /** Guidance only. Compares the house reading with the target for the flock's age. */
    public Climate climate(Long batchId, double temp, double humidity) {
        int day = health.dayOfLife(batchId), target = targetTemp(day);
        double diff = temp - target;
        List<Advice> a = new ArrayList<>();
        a.add(diff < -2 ? new Advice("Heat", "WARN", "Add heat", "The house is " + Math.round(-diff) + " °C under target. Switch on brooders or lamps and close draughts.")
            : diff > 5 ? new Advice("Heat", "BAD", "Heat stress risk", "The house is " + Math.round(diff) + " °C over target. Start all fans, open curtains, wet the roof and give cool water.")
            : diff > 2 ? new Advice("Heat", "WARN", "Getting warm", "The house is " + Math.round(diff) + " °C over target. Open curtains, add fans and lower any heat.")
            : new Advice("Heat", "OK", "Temperature is fine", "Within 2 °C of the target."));
        a.add(humidity > 70 ? new Advice("Ventilation", "WARN", "Too humid", "Wet litter and ammonia build up. Raise ventilation and turn or replace wet litter.")
            : humidity < 40 ? new Advice("Ventilation", "WARN", "Very dry air", "Dust irritates lungs. Keep ventilation steady and lightly damp the floor.")
            : diff > 2 ? new Advice("Ventilation", "WARN", "Add more air", "Warm air needs more ventilation. Raise fan and curtain settings.")
            : new Advice("Ventilation", "OK", "Air is fine", "Humidity is in a good range."));
        a.add(temp >= 30 || diff > 2 ? new Advice("Water", temp >= 34 ? "BAD" : "WARN", "Expect much more drinking", "Birds drink far more when hot. Check every drinker flows, add drinkers, keep water cool and never cut it back.")
            : new Advice("Water", "OK", "Water is normal", "Keep checking drinkers each morning."));
        return new Climate(day, target, a);
    }

    @Transactional(readOnly = true)
    public Profit profit(Long batchId) {
        var row = jdbc.query("SELECT batch_code, purchase_cost FROM poultry_batches WHERE id = ?", (rs, i) -> Map.entry(rs.getString(1), rs.getBigDecimal(2)), batchId)
            .stream().findFirst().orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found"));
        BigDecimal revenue = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM sales WHERE batch_id = ? AND status = 'OPEN'", BigDecimal.class, batchId);
        BigDecimal exp = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE batch_id = ?", BigDecimal.class, batchId);
        long alive = jdbc.queryForObject("SELECT COALESCE(SUM(quantity_change),0) FROM stock_movements WHERE batch_id = ?", Long.class, batchId);
        BigDecimal cost = row.getValue().add(exp), profit = revenue.subtract(cost);
        BigDecimal be = alive > 0 ? cost.subtract(revenue).max(BigDecimal.ZERO).divide(BigDecimal.valueOf(alive), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new Profit(row.getKey(), revenue, row.getValue(), exp, profit, alive, be, true);   // estimated until all costs are allocated
    }

    @Transactional(readOnly = true)
    public List<AgeBucket> ageing() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (String k : List.of("CURRENT", "1-30", "31-60", "61-90", "90+")) m.put(k, BigDecimal.ZERO);
        jdbc.query("""
            SELECT CASE WHEN d <= 0 THEN 'CURRENT' WHEN d <= 30 THEN '1-30' WHEN d <= 60 THEN '31-60' WHEN d <= 90 THEN '61-90' ELSE '90+' END, SUM(o)
            FROM (SELECT (?::date - s.due_date) AS d, s.total - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.sale_id = s.id), 0) AS o
                  FROM sales s WHERE s.status = 'OPEN') x WHERE o > 0 GROUP BY 1""",
            rs -> { m.put(rs.getString(1), rs.getBigDecimal(2)); }, time.today());
        return m.entrySet().stream().map(e -> new AgeBucket(e.getKey(), e.getValue())).toList();
    }
}
