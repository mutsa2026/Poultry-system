package com.poultry.config;

import com.poultry.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username).map(u -> {
            List<GrantedAuthority> a = new ArrayList<>();
            a.add(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
            if (u.isPermPay()) a.add(new SimpleGrantedAuthority("PERM_PAY"));
            if (u.isPermDiscount()) a.add(new SimpleGrantedAuthority("PERM_DISCOUNT"));
            if (u.isPermProfit()) a.add(new SimpleGrantedAuthority("PERM_PROFIT"));
            // enabled = active: FR-05, deactivated users cannot authenticate
            return (UserDetails) new User(u.getUsername(), u.getPasswordHash(), u.isActive(), true, true, true, a);
        }).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Bean AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder enc) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(uds);
        p.setPasswordEncoder(enc);
        return new ProviderManager(p);
    }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repo, SessionRegistry sessions) throws Exception {
        http
          .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                      .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                      .ignoringRequestMatchers("/api/v1/auth/login"))
          .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
          .addFilterAfter(new ExpiredSessionFilter(sessions), SecurityContextHolderFilter.class)
          .securityContext(sc -> sc.securityContextRepository(repo))
          .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
          .authorizeHttpRequests(a -> a
              .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/images/**",
                  "/batches", "/sales", "/customers", "/farm", "/analytics", "/medication",
                  "/map", "/more", "/account", "/processing", "/suppliers", "/team").permitAll()
              .requestMatchers("/api/v1/auth/login").permitAll()
              .requestMatchers("/actuator/health").permitAll()
              .anyRequest().authenticated())                       // NFR-01
          .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
          .httpBasic(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    static class ExpiredSessionFilter extends OncePerRequestFilter {
        private final SessionRegistry sessions;
        ExpiredSessionFilter(SessionRegistry sessions) { this.sessions = sessions; }

        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            if ("/api/v1/auth/login".equals(req.getServletPath())) {
                chain.doFilter(req, res);
                return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            HttpSession session = req.getSession(false);
            if (authentication != null && authentication.isAuthenticated() && session != null) {
                SessionInformation information = sessions.getSessionInformation(session.getId());
                if (information == null) {
                    sessions.registerNewSession(session.getId(), authentication.getName());
                } else if (information.isExpired()) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    res.sendError(HttpStatus.UNAUTHORIZED.value(), "Session expired. Sign in again.");
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }

    /** Makes Spring write the XSRF-TOKEN cookie on every response so the SPA can echo it back. */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken t = (CsrfToken) req.getAttribute("_csrf");
            if (t != null) t.getToken();
            chain.doFilter(req, res);
        }
    }
}
