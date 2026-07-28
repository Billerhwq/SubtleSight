package com.subtlesight.watchlist;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Models.ChangeEvent;
import com.subtlesight.domain.Models.WatchTarget;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Periodically scans all enabled watch targets and publishes changes via a callback.
 * <p>
 * Typical wiring: {@code @Scheduled(fixedDelayString = "PT1H")} in the Spring configuration,
 * with the SSE callback wired to {@code SseHub.publish()}.
 */
public final class WatchlistScheduler {

    private final IntelligenceRepository repository;
    private final WatchlistService service;
    private final ObjectMapper json;
    private final Clock clock;
    private final BiConsumer<String, Map<String, Object>> onChange; // (targetId, changeData) -> SSE

    public WatchlistScheduler(IntelligenceRepository repository, WatchlistService service,
                               ObjectMapper json, Clock clock,
                               BiConsumer<String, Map<String, Object>> onChange) {
        this.repository = repository;
        this.service = service;
        this.json = json;
        this.clock = clock;
        this.onChange = onChange;
    }

    /** Scan all enabled watch targets and detect changes. Called by the scheduler. */
    public void detectAll() {
        List<WatchTarget> targets = repository.listWatchTargets();
        Instant now = clock.instant();
        int totalChanges = 0;

        for (WatchTarget target : targets) {
            if (!target.enabled()) continue;
            // Skip targets still in cooldown
            if (target.cooldownUntil() != null && now.isBefore(target.cooldownUntil())) continue;

            try {
                // Build a "current" snapshot from the target's expression
                Map<String, Object> current = buildCurrentSnapshot(target);
                List<ChangeEvent> changes = service.detect(target.id(), current, "scheduler");

                if (!changes.isEmpty()) {
                    totalChanges += changes.size();
                    // Publish each change via SSE
                    for (ChangeEvent event : changes) {
                        onChange.accept(target.id().toString(), Map.of(
                                "targetId", target.id().toString(),
                                "targetName", target.name(),
                                "field", event.field(),
                                "oldValue", event.oldValue() != null ? event.oldValue() : "",
                                "newValue", event.newValue() != null ? event.newValue() : "",
                                "severity", event.severity().name(),
                                "detectedAt", event.detectedAt().toString()
                        ));
                    }
                }
            } catch (Exception e) {
                // Skip failed targets; continue with next
                onChange.accept(target.id().toString(), Map.of(
                        "targetId", target.id().toString(),
                        "targetName", target.name(),
                        "error", e.getMessage()
                ));
            }
        }
    }

    /** Build a simple current-value snapshot from the watch target expression. */
    private Map<String, Object> buildCurrentSnapshot(WatchTarget target) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        try {
            // Parse the expression as simple key-value pairs or JSON
            String expr = target.expression();
            if (expr == null || expr.isBlank()) return snapshot;

            // Try JSON first
            if (expr.trim().startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = json.readValue(expr, Map.class);
                snapshot.putAll(parsed);
            } else {
                // Treat as key:value pairs (e.g. "status:active, version:2.0")
                for (String pair : expr.split(",")) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String value = kv[1].trim();
                        // Try parsing numbers
                        try { snapshot.put(key, Long.parseLong(value)); continue; } catch (NumberFormatException ignored) {}
                        try { snapshot.put(key, Double.parseDouble(value)); continue; } catch (NumberFormatException ignored) {}
                        snapshot.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            snapshot.put("_error", "parse failed: " + e.getMessage());
        }
        // Add a timestamp marker
        snapshot.put("_checkedAt", clock.instant().toString());
        return snapshot;
    }
}
