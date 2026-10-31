package com.poultry.feed;

import com.poultry.service.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {
    public record FeedStock(Long id, String name, String unit, BigDecimal onHand, BigDecimal reorderLevel, boolean low) {}

    private final JdbcTemplate jdbc; private final AuditService audit; private final CurrentUser me;
    public FeedService(JdbcTemplate j, AuditService a, CurrentUser m) { jdbc = j; audit = a; me = m; }

    @Transactional(readOnly = true)
    public List<FeedStock> stock() {
        return jdbc.query("""
            SELECT f.id, f.name, f.unit, f.reorder_level, COALESCE(SUM(m.quantity_change), 0)
            FROM feed_types f LEFT JOIN feed_movements m ON m.feed_type_id = f.id GROUP BY f.id ORDER BY f.name""",
            (rs, i) -> { BigDecimal on = rs.getBigDecimal(5), lvl = rs.getBigDecimal(4);
                return new FeedStock(rs.getLong(1), rs.getString(2), rs.getString(3), on, lvl, on.compareTo(lvl) < 0); });
    }

    public Long defaultFeedType() {
        return jdbc.queryForObject("SELECT id FROM feed_types ORDER BY id LIMIT 1", Long.class);
    }

    @Transactional
    public void receive(Long feedTypeId, BigDecimal qty, String notes) {
        jdbc.update("INSERT INTO feed_movements(feed_type_id, quantity_change, kind, notes, created_by) VALUES (?,?,'RECEIPT',?,?)",
            feedTypeId, qty, notes, me.id());
    }

    /** FR-12: record feed used by a batch; refuses to take stock below zero. */
    @Transactional
    public FeedStock consume(Long feedTypeId, Long batchId, BigDecimal qty, String notes) {
        if (qty == null || qty.signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity must be above 0");
        if (jdbc.queryForList("SELECT id FROM feed_types WHERE id = ? FOR UPDATE", Long.class, feedTypeId).isEmpty())
            throw new BusinessException(HttpStatus.NOT_FOUND, "Feed type not found");
        BigDecimal on = jdbc.queryForObject("SELECT COALESCE(SUM(quantity_change),0) FROM feed_movements WHERE feed_type_id = ?", BigDecimal.class, feedTypeId);
        if (qty.compareTo(on) > 0) throw new BusinessException(HttpStatus.CONFLICT, "Only " + on + " in store, so you can’t use " + qty);
        jdbc.update("INSERT INTO feed_movements(feed_type_id, quantity_change, kind, batch_id, notes, created_by) VALUES (?,?,'CONSUMPTION',?,?,?)",
            feedTypeId, qty.negate(), batchId, notes, me.id());
        audit.record("FEED_USED", "feed_types", feedTypeId, "qty=" + qty + " batch=" + batchId);
        return stock().stream().filter(f -> f.id().equals(feedTypeId)).findFirst().orElseThrow();
    }
}
