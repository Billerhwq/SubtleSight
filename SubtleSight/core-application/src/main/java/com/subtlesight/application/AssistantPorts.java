package com.subtlesight.application;

import com.subtlesight.domain.AssistantModels.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AssistantPorts {
    private AssistantPorts() {}

    public interface Repository {

        // ── Sessions ──
        AssistantSession createSession(String title);
        Optional<AssistantSession> getSession(UUID sessionId);
        List<AssistantSession> listSessions(int limit, int offset);
        void updateSessionTitle(UUID sessionId, String title);
        void archiveSession(UUID sessionId);

        // ── Turns ──
        /** Append a turn and return its id. */
        UUID appendTurn(UUID sessionId, Role role, String content, String contextJson);
        Optional<AssistantTurn> getTurn(UUID turnId);
        /** Update status, plan, and tools for a turn. */
        void updateTurnStatus(UUID turnId, TurnStatus status, String planJson, String toolsJson);
        List<AssistantTurn> listTurns(UUID sessionId, int limit);

        // ── Audit ──
        void appendAudit(UUID turnId, String action, String detailJson);
    }
}
