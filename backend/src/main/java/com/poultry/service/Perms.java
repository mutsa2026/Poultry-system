package com.poultry.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Perms {
    public boolean has(String authority) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals(authority));
    }
    public boolean owner() { return has("ROLE_OWNER"); }
    public boolean can(String perm) { return owner() || has(perm); }
}
