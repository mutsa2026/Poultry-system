package com.poultry.domain;

import jakarta.persistence.*;

@Entity @Table(name = "users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "perm_pay", nullable = false) private boolean permPay;
    @Column(name = "perm_discount", nullable = false) private boolean permDiscount;
    @Column(name = "perm_profit", nullable = false) private boolean permProfit;

    public enum Role { OWNER, WORKER }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { role = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public boolean isPermPay() { return permPay; }
    public void setPermPay(boolean v) { permPay = v; }
    public boolean isPermDiscount() { return permDiscount; }
    public void setPermDiscount(boolean v) { permDiscount = v; }
    public boolean isPermProfit() { return permProfit; }
    public void setPermProfit(boolean v) { permProfit = v; }
}
