package com.poultry.config;

import com.poultry.domain.AppUser;
import com.poultry.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates the first owner from env vars (OWNER_USERNAME / OWNER_PASSWORD) when the users table is empty. */
@Configuration
public class OwnerBootstrap {
    @Bean CommandLineRunner seedOwner(UserRepository users, PasswordEncoder enc,
            @Value("${OWNER_USERNAME:}") String username, @Value("${OWNER_PASSWORD:}") String password) {
        return args -> {
            if (users.count() > 0 || username.isBlank() || password.isBlank()) return;
            AppUser u = new AppUser();
            u.setUsername(username); u.setFullName("Owner"); u.setRole(AppUser.Role.OWNER);
            u.setPasswordHash(enc.encode(password));
            users.save(u);
        };
    }
}
