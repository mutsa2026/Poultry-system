package com.poultry.user;

import com.poultry.domain.AppUser;
import com.poultry.repo.UserRepository;
import com.poultry.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('OWNER')")
public class UserAdminController {
    public record UserView(Long id, String username, String fullName, String role, boolean active, boolean pay, boolean discount, boolean profit) {}
    public record CreateUser(@NotBlank @Size(max = 60) String username, @NotBlank String fullName,
                             @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password) {}
    public record Status(boolean active) {}
    public record Permissions(boolean pay, boolean discount, boolean profit) {}

    private final UserRepository users; private final PasswordEncoder enc; private final AuditService audit; private final CurrentUser me;
    private final JdbcTemplate jdbc;
    public UserAdminController(UserRepository u, PasswordEncoder e, AuditService a, CurrentUser m, JdbcTemplate j) { users = u; enc = e; audit = a; me = m; jdbc = j; }

    private static UserView view(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(), u.isActive(), u.isPermPay(), u.isPermDiscount(), u.isPermProfit());
    }

    @GetMapping public List<UserView> list() { return users.findAll().stream().map(UserAdminController::view).toList(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public UserView create(@Valid @RequestBody CreateUser r) {
        if (users.findByUsername(r.username()).isPresent()) throw new BusinessException(HttpStatus.CONFLICT, "Username already taken");
        AppUser u = new AppUser();
        u.setUsername(r.username()); u.setFullName(r.fullName()); u.setRole(AppUser.Role.WORKER); u.setPasswordHash(enc.encode(r.password()));
        u = users.save(u);
        audit.record("USER_CREATED", "users", u.getId(), u.getUsername());
        return view(u);
    }

    @PatchMapping("/{id}/status") @Transactional
    public UserView status(@PathVariable Long id, @RequestBody Status s) {
        AppUser u = users.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getId().equals(me.id())) throw new BusinessException(HttpStatus.CONFLICT, "You can’t deactivate your own account");
        u.setActive(s.active());
        audit.record(s.active() ? "USER_ACTIVATED" : "USER_DEACTIVATED", "users", id, u.getUsername());
        return view(u);
    }

    @PatchMapping("/{id}/permissions") @Transactional
    public UserView permissions(@PathVariable Long id, @RequestBody Permissions p) {
        AppUser u = users.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() == AppUser.Role.OWNER) throw new BusinessException(HttpStatus.CONFLICT, "Owner permissions can’t be changed");
        u.setPermPay(p.pay()); u.setPermDiscount(p.discount()); u.setPermProfit(p.profit());
        audit.record("USER_PERMISSIONS_CHANGED", "users", id, "pay=" + p.pay() + " discount=" + p.discount() + " profit=" + p.profit());
        return view(u);
    }

    public record AuditRow(Long id, String actor, String action, String entityType, Long entityId, String details, java.time.Instant at) {}
    @GetMapping("/audit")
    public List<AuditRow> audit(@RequestParam(defaultValue = "50") int limit) {
        return jdbc.query("""
            SELECT a.id, COALESCE(u.username,'system'), a.action, a.entity_type, a.entity_id, a.details, a.created_at
            FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id ORDER BY a.id DESC LIMIT ?""",
            (rs, i) -> new AuditRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                (Long) rs.getObject(5), rs.getString(6), rs.getTimestamp(7).toInstant()), Math.min(limit, 200));
    }
}
