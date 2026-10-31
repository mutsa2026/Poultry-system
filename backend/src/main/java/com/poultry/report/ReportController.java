package com.poultry.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports; private final AnalyticsService analytics; private final ExportService exports;
    public ReportController(ReportService r, AnalyticsService a, ExportService e) { reports = r; analytics = a; exports = e; }

    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','PERM_PROFIT')")
    @GetMapping("/reports/analytics") public AnalyticsService.Analytics analytics() { return analytics.build(); }

    @GetMapping("/reports/export/{name}")
    public ResponseEntity<byte[]> export(@PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = exports.csv(name, from, to).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"poultry-" + name + ".csv\"")
            .header("X-Content-Type-Options", "nosniff")
            .body(body);
    }

    @GetMapping("/dashboard") public ReportService.Dashboard dashboard() { return reports.dashboard(); }

    @GetMapping("/batches/{id}/mortality-daily")
    public List<ReportService.DayCount> daily(@PathVariable Long id, @RequestParam(defaultValue = "14") int days) { return reports.mortalityDaily(id, days); }

    @GetMapping("/batches/{id}/climate")
    public ReportService.Climate climate(@PathVariable Long id, @RequestParam double temp, @RequestParam double humidity) { return reports.climate(id, temp, humidity); }

    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','PERM_PROFIT')")
    @GetMapping("/reports/profit") public ReportService.Profit profit(@RequestParam Long batchId) { return reports.profit(batchId); }

    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','PERM_PROFIT')")
    @GetMapping("/reports/receivables-ageing") public List<ReportService.AgeBucket> ageing() { return reports.ageing(); }
}
