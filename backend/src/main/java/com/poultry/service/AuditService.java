package com.poultry.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the caller's transaction, so an audit row is committed or rolled back with the change it describes. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    public AuditService(JdbcTemplate jdbc, CurrentUser currentUser) { this.jdbc = jdbc; this.currentUser = currentUser; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Long entityId, String details) {
        jdbc.update("INSERT INTO audit_logs(actor_id, action, entity_type, entity_id, details) VALUES (?,?,?,?,?)",
            currentUser.id(), action, entityType, entityId, details);
    }
}
