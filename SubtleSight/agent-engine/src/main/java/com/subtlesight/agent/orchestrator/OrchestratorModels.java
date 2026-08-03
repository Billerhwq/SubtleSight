package com.subtlesight.agent.orchestrator;

import com.subtlesight.agent.tools.ToolModels.ToolResult;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Value objects for the plan→execute→verify orchestration pipeline. */
public final class OrchestratorModels {
    private OrchestratorModels() {}

    public record PlanStep(int ordinal, String tool, String description, Map<String, Object> params) {
        public PlanStep {
            Objects.requireNonNull(tool);
            requireText(description, "description");
            params = params == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    public record Plan(UUID id, List<PlanStep> steps, String rationale) {
        public Plan {
            Objects.requireNonNull(id);
            steps = steps == null ? List.of() : List.copyOf(steps);
            rationale = rationale == null ? "" : rationale;
        }
    }

    public record ToolExecution(int ordinal, String tool, Map<String, Object> result,
                                 boolean success, String error, ToolResult protocolResult) {
        public ToolExecution(int ordinal, String tool, Map<String, Object> result,
                             boolean success, String error) {
            this(ordinal, tool, result, success, error, null);
        }

        public ToolExecution {
            Objects.requireNonNull(tool);
            result = result == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(result));
            error = error == null ? "" : error;
        }
    }

    /** A structured citation source attached to a synthesized answer. */
    public record CitationReference(
            int index, String resourceId, String resourceType, String resourceName,
            String url, String publishedAt, String summary, String locator, String exactQuote) {
        public CitationReference {
            resourceId = resourceId == null ? "" : resourceId;
            resourceType = resourceType == null ? "" : resourceType;
            resourceName = resourceName == null ? "" : resourceName;
            url = url == null ? "" : url;
            publishedAt = publishedAt == null ? "" : publishedAt;
            summary = summary == null ? "" : summary;
            locator = locator == null ? "" : locator;
            exactQuote = exactQuote == null ? "" : exactQuote;
        }
    }

    public record OrchestrationResult(
            UUID turnId, List<ToolExecution> executions, String summary,
            boolean confirmationRequired, String confirmingTool,
            List<CitationReference> references) {
        public OrchestrationResult(UUID turnId, List<ToolExecution> executions, String summary,
                                   boolean confirmationRequired, String confirmingTool) {
            this(turnId, executions, summary, confirmationRequired, confirmingTool, List.of());
        }

        public OrchestrationResult {
            executions = executions == null ? List.of() : List.copyOf(executions);
            summary = summary == null ? "" : summary;
            confirmingTool = confirmingTool == null ? "" : confirmingTool;
            references = references == null ? List.of() : List.copyOf(references);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
