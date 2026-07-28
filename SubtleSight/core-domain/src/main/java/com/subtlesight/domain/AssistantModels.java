package com.subtlesight.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable domain contracts for the assistant session / turn / audit triad. */
public final class AssistantModels {
    private AssistantModels() {}

    public enum Role { USER, ASSISTANT, SYSTEM }

    public enum TurnStatus {
        PENDING, PLANNING, EXECUTING, AWAITING_CONFIRM, STREAMING, COMPLETED, FAILED, CANCELLED
    }

    public record AssistantSession(
            UUID id, String title, Instant createdAt, Instant updatedAt, boolean archived) {
        public AssistantSession {
            Objects.requireNonNull(id);
            requireText(title, "title");
            Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
        }
    }

    public record AssistantTurn(
            UUID id, UUID sessionId, Role role, String content, TurnStatus status,
            String planJson, String toolsJson, String contextJson,
            Instant createdAt, Instant completedAt) {
        public AssistantTurn {
            Objects.requireNonNull(id);
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(role);
            requireText(content, "content");
            Objects.requireNonNull(status);
            Objects.requireNonNull(createdAt);
        }
    }

    public record AssistantAudit(
            UUID id, UUID turnId, String action, String detailJson, Instant createdAt) {
        public AssistantAudit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(turnId);
            requireText(action, "action");
            Objects.requireNonNull(createdAt);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
