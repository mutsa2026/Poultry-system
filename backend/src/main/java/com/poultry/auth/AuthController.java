package com.poultry.auth;

import com.poultry.domain.AppUser;
import com.poultry.repo.UserRepository;
import com.poultry.service.AuditService;
import com.poultry.service.BusinessException;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.*;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {}
    public record Me(Long id, String username, String fullName, String role, List<String> permissions) {}

    private final AuthenticationManager manager;
    private final SecurityContextRepository repo;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SessionRegistry sessions;

    public AuthController(AuthenticationManager m, SecurityContextRepository r, UserRepository u,
            PasswordEncoder p, AuditService a, SessionRegistry s) {
        manager = m; repo = r; users = u; passwordEncoder = p; audit = a; sessions = s;
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
        Authentication a;
        try {
            a = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(r.username(), r.password()));
        } catch (AuthenticationException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Wrong username or password, or the account is deactivated");
        }
        HttpSession previous = req.getSession(false);
        if (previous != null) previous.invalidate();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(a);
        SecurityContextHolder.setContext(ctx);
        repo.saveContext(ctx, req, res);
        HttpSession session = req.getSession(false);
        if (session != null) sessions.registerNewSession(session.getId(), a.getName());
        return toMe(a);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest r, Authentication authentication) {
        AppUser user = users.findByUsername(authentication.getName())
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Please sign in again"));
        if (!passwordEncoder.matches(r.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(r.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose a password different from your current password");
        }

        user.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        audit.record("USER_PASSWORD_CHANGED", "users", user.getId(), "Password changed; active sessions expired");
        for (SessionInformation session : sessions.getAllSessions(authentication.getName(), true)) {
            session.expireNow();
        }
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public Me me(Authentication a) { return toMe(a); }

    private Me toMe(Authentication a) {
        AppUser u = users.findByUsername(a.getName()).orElseThrow();
        List<String> perms = a.getAuthorities().stream().map(g -> g.getAuthority()).filter(x -> x.startsWith("PERM_")).toList();
        return new Me(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(), perms);
    }
}
