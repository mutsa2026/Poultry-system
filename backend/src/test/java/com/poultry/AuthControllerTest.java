package com.poultry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poultry.auth.AuthController;
import com.poultry.domain.AppUser;
import com.poultry.repo.UserRepository;
import com.poultry.service.AuditService;
import com.poultry.service.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.authentication.AuthenticationManager;

class AuthControllerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final SessionRegistryImpl sessions = new SessionRegistryImpl();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
        "owner", null, List.of());
    private AppUser owner;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        owner = new AppUser();
        owner.setUsername("owner");
        owner.setPasswordHash(passwordEncoder.encode("old-password"));
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        controller = new AuthController(mock(AuthenticationManager.class), mock(SecurityContextRepository.class),
            users, passwordEncoder, audit, sessions);
    }

    @Test
    void changesPasswordAndExpiresEveryRegisteredSession() {
        sessions.registerNewSession("current-session", "owner");
        sessions.registerNewSession("other-device-session", "owner");

        controller.changePassword(new AuthController.ChangePasswordRequest("old-password", "new-password"), authentication);

        assertTrue(passwordEncoder.matches("new-password", owner.getPasswordHash()));
        assertFalse(passwordEncoder.matches("old-password", owner.getPasswordHash()));
        assertTrue(sessions.getAllSessions("owner", true).stream().allMatch(info -> info.isExpired()));
        verify(audit).record("USER_PASSWORD_CHANGED", "users", null, "Password changed; active sessions expired");
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutChangingPasswordOrSessions() {
        sessions.registerNewSession("current-session", "owner");

        assertThrows(BusinessException.class, () -> controller.changePassword(
            new AuthController.ChangePasswordRequest("incorrect-password", "new-password"), authentication));

        assertTrue(passwordEncoder.matches("old-password", owner.getPasswordHash()));
        assertFalse(sessions.getSessionInformation("current-session").isExpired());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsReusingCurrentPassword() {
        assertThrows(BusinessException.class, () -> controller.changePassword(
            new AuthController.ChangePasswordRequest("old-password", "old-password"), authentication));

        assertTrue(passwordEncoder.matches("old-password", owner.getPasswordHash()));
        verifyNoInteractions(audit);
    }
}
