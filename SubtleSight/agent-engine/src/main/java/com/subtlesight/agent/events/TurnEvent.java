package com.subtlesight.agent.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.subtlesight.agent.tools.ToolModels.ToolResult;

/**
 * Typed SSE event for a turn's lifecycle.
 * <p>
 * Standard event types:
 * <ul>
 *   <li>{@code planning_started} — orchestrator begins</li>
 *   <li>{@code plan_created} — LLM/keyword plan ready</li>
 *   <li>{@code step_started} — tool execution begins</li>
 *   <li>{@code step_completed} — tool execution finishes</li>
 *   <li>{@code confirmation_required} — high-risk step paused</li>
 *   <li>{@code streaming_chunk} — LLM token streaming</li>
 *   <li>{@code turn_completed} — all steps done</li>
 *   <li>{@code error} — orchestration failed</li>
 * </ul>
 */
public record TurnEvent(
        String turnId,
        String type,
        Map<String, Object> data,
        Instant timestamp) {

    public TurnEvent {
        Objects.requireNonNull(turnId);
        requireText(type, "type");
        data = data == null ? Map.of() : Map.copyOf(data);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    // ── Factory methods ──

    public static TurnEvent planningStarted(UUID turnId) {
        return new TurnEvent(turnId.toString(), "planning_started",
                Map.of("turnId", turnId.toString()), Instant.now());
    }

    public static TurnEvent planCreated(UUID turnId, int steps, String rationale,
                                         java.util.List<Map<String, Object>> stepList) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turnId.toString());
        data.put("steps", steps);
        data.put("rationale", rationale);
        data.put("stepList", stepList);
        return new TurnEvent(turnId.toString(), "plan_created", data, Instant.now());
    }

    public static TurnEvent stepStarted(UUID turnId, int ordinal, String tool, String description) {
        return stepStarted(turnId, ordinal, tool, description, null, null, null, null);
    }

    public static TurnEvent stepStarted(UUID turnId, int ordinal, String tool, String description,
                                        String callId, String toolId, String toolVersion, String label) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turnId.toString());
        data.put("ordinal", ordinal);
        data.put("toolName", tool);
        data.put("description", description);
        if (callId != null) data.put("callId", callId);
        if (toolId != null) data.put("toolId", toolId);
        if (toolVersion != null) data.put("toolVersion", toolVersion);
        if (label != null) data.put("label", label);
        return new TurnEvent(turnId.toString(), "step_started", data, Instant.now());
    }

    public static TurnEvent stepCompleted(UUID turnId, int ordinal, String tool,
                                           boolean success, Map<String, Object> result) {
        return stepCompleted(turnId, ordinal, tool, success, result, null);
    }

    public static TurnEvent stepCompleted(UUID turnId, int ordinal, String tool,
                                           boolean success, Map<String, Object> result,
                                           ToolResult protocolResult) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turnId.toString());
        data.put("ordinal", ordinal);
        data.put("toolName", tool);
        data.put("success", success);
        data.put("result", result);
        if (protocolResult != null) {
            data.put("callId", protocolResult.callId());
            data.put("toolId", protocolResult.toolId());
            data.put("toolVersion", protocolResult.toolVersion());
            data.put("toolResult", protocolResult.toMap());
        }
        return new TurnEvent(turnId.toString(), "step_completed", data, Instant.now());
    }

    public static TurnEvent toolProgress(UUID turnId, int ordinal, String callId,
                                          String toolName, String toolId, String toolVersion,
                                          String phase, String summary, String detail, Double progress) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "tool.progress");
        data.put("eventId", "evt_" + UUID.randomUUID());
        data.put("turnId", turnId.toString());
        data.put("ordinal", ordinal);
        data.put("callId", callId);
        data.put("toolName", toolName);
        data.put("toolId", toolId);
        data.put("toolVersion", toolVersion);
        data.put("phase", phase);
        data.put("summary", summary);
        if (detail != null) data.put("detail", detail);
        if (progress != null) data.put("progress", progress);
        return new TurnEvent(turnId.toString(), "tool_progress", data, Instant.now());
    }

    public static TurnEvent stepChunk(UUID turnId, int ordinal, String chunk) {
        return new TurnEvent(turnId.toString(), "step_chunk", Map.of(
                "turnId", turnId.toString(), "ordinal", ordinal, "chunk", chunk), Instant.now());
    }

    public static TurnEvent confirmationRequired(UUID turnId, String tool, String description) {
        return new TurnEvent(turnId.toString(), "confirmation_required", Map.of(
                "turnId", turnId.toString(), "toolName", tool,
                "description", description), Instant.now());
    }

    public static TurnEvent streamingChunk(UUID turnId, String chunk) {
        return new TurnEvent(turnId.toString(), "streaming_chunk",
                Map.of("turnId", turnId.toString(), "chunk", chunk), Instant.now());
    }

    public static TurnEvent turnCompleted(UUID turnId, int executions, boolean allSuccess) {
        return turnCompleted(turnId, executions, allSuccess, null, null);
    }

    public static TurnEvent turnCompleted(UUID turnId, int executions, boolean allSuccess,
                                          String answer, List<Map<String, Object>> references) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turnId.toString());
        data.put("executions", executions);
        data.put("allSuccess", allSuccess);
        if (answer != null) data.put("answer", answer);
        if (references != null && !references.isEmpty()) data.put("references", references);
        return new TurnEvent(turnId.toString(), "turn_completed", data, Instant.now());
    }

    public static TurnEvent error(UUID turnId, String message) {
        return new TurnEvent(turnId.toString(), "error",
                Map.of("turnId", turnId.toString(), "message", message), Instant.now());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
