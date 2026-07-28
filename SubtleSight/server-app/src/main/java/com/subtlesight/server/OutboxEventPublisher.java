package com.subtlesight.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.agent.events.TurnEvent;
import com.subtlesight.agent.events.TurnEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Write-through TurnEventPublisher that persists every event to
 * {@code outbox_events} before forwarding to SSE.
 * <p>
 * Unpublished events survive restarts; the periodic drain picks them up.
 */
public class OutboxEventPublisher implements TurnEventPublisher {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SseHub sseHub;
    private final ObjectMapper json;

    /** Downstream publisher (optional extra sink). */
    private TurnEventPublisher downstream;

    public OutboxEventPublisher(DataSource dataSource, Clock clock, SseHub sseHub, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clock;
        this.sseHub = sseHub;
        this.json = json;
    }

    public void setDownstream(TurnEventPublisher downstream) { this.downstream = downstream; }

    // ── TurnEventPublisher ──

    @Override
    public void publish(TurnEvent event) {
        Instant now = clock.instant();
        try {
            String payload = json.writeValueAsString(Map.of(
                    "turnId", event.turnId(),
                    "type", event.type(),
                    "data", event.data(),
                    "timestamp", event.timestamp().toString()
            ));
            jdbc.update("""
                    INSERT INTO outbox_events(id, aggregate_type, aggregate_id, event_type, payload_json, created_at)
                    VALUES(?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), "assistant_turn", event.turnId(),
                    event.type(), payload, now.toString());
        } catch (Exception e) {
            // Fallback: publish directly if outbox write fails
            sseHub.publishToTurn(event.turnId(), event.type(), event.data());
        }

        // Also forward immediately to SSE for real-time delivery
        sseHub.publishToTurn(event.turnId(), event.type(), event.data());
        if (downstream != null) downstream.publish(event);
    }

    // ── Periodic drain ──

    /** Every 5 seconds, re-publish any events that weren't delivered. */
    @Scheduled(fixedDelay = 5_000)
    public void drainOutbox() {
        List<OutboxRow> rows = jdbc.query(
                "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at LIMIT 100",
                (rs, n) -> new OutboxRow(
                        rs.getString("id"), rs.getString("aggregate_id"),
                        rs.getString("event_type"), rs.getString("payload_json")));

        Instant now = clock.instant();
        for (OutboxRow row : rows) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = json.readValue(row.payloadJson(), Map.class);
                String turnId = (String) payload.getOrDefault("turnId", row.aggregateId());
                String type = (String) payload.getOrDefault("type", row.eventType());
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());
                sseHub.publishToTurn(turnId, type, data);
                jdbc.update("UPDATE outbox_events SET published_at=? WHERE id=?", now.toString(), row.id());
            } catch (Exception ignored) {
                // Skip malformed events
            }
        }
    }

    /** Archive events older than 7 days. */
    @Scheduled(fixedDelay = 3_600_000) // hourly
    public void archiveOldEvents() {
        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(7));
        jdbc.update("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND created_at < ?",
                cutoff.toString());
    }

    private record OutboxRow(String id, String aggregateId, String eventType, String payloadJson) {}
}
