package com.poultry.config;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;

class ExpiredSessionFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesExpiredSessionAndReturnsUnauthorized() throws ServletException, IOException {
        var sessions = new SessionRegistryImpl();
        var session = new MockHttpSession();
        sessions.registerNewSession(session.getId(), "owner");
        sessions.getSessionInformation(session.getId()).expireNow();
        var request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/auth/me");
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("owner", null, List.of()));

        new SecurityConfig.ExpiredSessionFilter(sessions).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());
        assertNull(chain.getRequest());
    }

    @Test
    void expiredSessionCanStillSubmitLogin() throws ServletException, IOException {
        var sessions = new SessionRegistryImpl();
        var session = new MockHttpSession();
        sessions.registerNewSession(session.getId(), "owner");
        sessions.getSessionInformation(session.getId()).expireNow();
        var request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/auth/login");
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new SecurityConfig.ExpiredSessionFilter(sessions).doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertFalse(session.isInvalid());
        assertEquals(200, response.getStatus());
    }
}
