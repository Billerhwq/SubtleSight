package com.subtlesight.agent.orchestrator;

import java.util.List;
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
            params = params == null ? Map.of() : Map.copyOf(params);
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
                                 boolean success, String error) {
        public ToolExecution {
            Objects.requireNonNull(tool);
            result = result == null ? Map.of() : Map.copyOf(result);
            error = error == null ? "" : error;
        }
    }

    public record OrchestrationResult(
            UUID turnId, List<ToolExecution> executions, String summary,
            boolean confirmationRequired, String confirmingTool) {
        public OrchestrationResult {
            executions = executions == null ? List.of() : List.copyOf(executions);
            summary = summary == null ? "" : summary;
            confirmingTool = confirmingTool == null ? "" : confirmingTool;
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
