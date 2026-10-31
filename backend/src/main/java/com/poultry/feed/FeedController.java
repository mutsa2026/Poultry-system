package com.poultry.feed;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {
    public record UseRequest(@NotNull Long feedTypeId, Long batchId, @NotNull @Positive BigDecimal quantity, @Size(max = 255) String notes) {}
    private final FeedService feed;
    public FeedController(FeedService f) { feed = f; }

    @GetMapping("/stock") public List<FeedService.FeedStock> stock() { return feed.stock(); }

    @PostMapping("/consumption")
    public FeedService.FeedStock use(@Valid @RequestBody UseRequest r) { return feed.consume(r.feedTypeId(), r.batchId(), r.quantity(), r.notes()); }
}
