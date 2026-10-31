package com.poultry.health;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    public record VisitorRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 30) String purpose, @Size(max = 20) String plate, boolean footbath, boolean poultryContact) {}
    private final HealthService health;
    public HealthController(HealthService h) { health = h; }

    @GetMapping("/batches/{id}/vaccinations") public List<HealthService.VaccineItem> schedule(@PathVariable Long id) { return health.schedule(id); }
    @PostMapping("/batches/{id}/vaccinations/{day}/done") public List<HealthService.VaccineItem> done(@PathVariable Long id, @PathVariable int day) { return health.markDone(id, day); }
    @GetMapping("/visitors") public List<HealthService.VisitorView> visitors() { return health.visitors(30); }
    @PostMapping("/visitors") @ResponseStatus(HttpStatus.CREATED)
    public HealthService.VisitorView log(@Valid @RequestBody VisitorRequest r) { return health.logVisitor(new HealthService.LogVisitor(r.name(), r.purpose(), r.plate(), r.footbath(), r.poultryContact())); }
}
