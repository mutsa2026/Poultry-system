package com.poultry.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** NFR-14: one configured business timezone for every "today" calculation. */
@Component
public class BusinessTime {
    private final ZoneId zone;
    public BusinessTime(@Value("${app.business-timezone:Africa/Harare}") String zone) { this.zone = ZoneId.of(zone); }
    public String zoneId() { return zone.getId(); }
    public LocalDate today() { return LocalDate.now(zone); }
    public Instant startOfDay(LocalDate d) { return d.atStartOfDay(zone).toInstant(); }
}
