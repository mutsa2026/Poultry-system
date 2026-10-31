package com.poultry.health;

import com.poultry.service.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {
    public record VaccineItem(int day, String vaccine, LocalDate dueDate, String status, Instant doneAt) {}
    public record VisitorView(Long id, String name, String purpose, String plate, boolean footbath, boolean poultryContact, Instant at, boolean risk) {}
    public record LogVisitor(String name, String purpose, String plate, boolean footbath, boolean poultryContact) {}

    /** Example schedule only. A vet must confirm the real programme. */
    static final int[] DAYS = {1, 7, 14, 21, 28};
    static final String[] NAMES = {"Newcastle and IB (hatchery)", "Newcastle and IB", "Gumboro (IBD)", "Gumboro booster", "Newcastle booster"};

    private final JdbcTemplate jdbc; private final BusinessTime time; private final AuditService audit; private final CurrentUser me;
    public HealthService(JdbcTemplate j, BusinessTime t, AuditService a, CurrentUser m) { jdbc = j; time = t; audit = a; me = m; }

    private LocalDate arrival(Long batchId) {
        return jdbc.query("SELECT arrival_date FROM poultry_batches WHERE id = ?", (rs, i) -> rs.getDate(1).toLocalDate(), batchId).stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Batch not found"));
    }

    public int dayOfLife(Long batchId) { return (int) ChronoUnit.DAYS.between(arrival(batchId), time.today()) + 1; }

    @Transactional(readOnly = true)
    public List<VaccineItem> schedule(Long batchId) {
        LocalDate arr = arrival(batchId); int age = dayOfLife(batchId);
        Map<Integer, Instant> done = new HashMap<>();
        jdbc.query("SELECT day_number, done_at FROM vaccination_records WHERE batch_id = ?", rs -> { done.put(rs.getInt(1), rs.getTimestamp(2).toInstant()); }, batchId);
        List<VaccineItem> out = new ArrayList<>();
        for (int i = 0; i < DAYS.length; i++) {
            int d = DAYS[i];
            String st = done.containsKey(d) ? "DONE" : age > d ? "OVERDUE" : age == d ? "DUE" : "UPCOMING";
            out.add(new VaccineItem(d, NAMES[i], arr.plusDays(d - 1), st, done.get(d)));
        }
        return out;
    }

    @Transactional
    public List<VaccineItem> markDone(Long batchId, int day) {
        int idx = -1; for (int i = 0; i < DAYS.length; i++) if (DAYS[i] == day) idx = i;
        if (idx < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Day " + day + " isn’t in the schedule");
        if (dayOfLife(batchId) < day) throw new BusinessException(HttpStatus.CONFLICT, "That vaccination isn’t due yet");
        jdbc.update("INSERT INTO vaccination_records(batch_id, day_number, vaccine, done_by) VALUES (?,?,?,?) ON CONFLICT (batch_id, day_number) DO NOTHING", batchId, day, NAMES[idx], me.id());
        audit.record("VACCINATION_DONE", "poultry_batches", batchId, "day " + day + " " + NAMES[idx]);
        return schedule(batchId);
    }

    @Transactional
    public VisitorView logVisitor(LogVisitor r) {
        Long id = jdbc.queryForObject("INSERT INTO visitor_log(visitor_name, purpose, vehicle_plate, footbath_used, poultry_contact, logged_by) VALUES (?,?,?,?,?,?) RETURNING id",
            Long.class, r.name(), r.purpose(), r.plate(), r.footbath(), r.poultryContact(), me.id());
        return visitors(1).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<VisitorView> visitors(int limit) {
        return jdbc.query("SELECT id, visitor_name, purpose, vehicle_plate, footbath_used, poultry_contact, logged_at FROM visitor_log ORDER BY id DESC LIMIT ?",
            (rs, i) -> new VisitorView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6),
                rs.getTimestamp(7).toInstant(), !rs.getBoolean(5) || rs.getBoolean(6)), Math.min(limit, 100));
    }
}
