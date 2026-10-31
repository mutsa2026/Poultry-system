package com.poultry.service;

import com.poultry.repo.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }

    public Long id() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByUsername(name).orElseThrow().getId();
    }
}
