package com.subtlesight.server;

import com.subtlesight.agent.WebAgentService;
import com.subtlesight.application.AssistantPorts;
import com.subtlesight.domain.Models.AgentRequest;
import com.subtlesight.domain.Models.AgentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final WebAgentService agentService;
    private final SseHub sseHub;
    private final AssistantPorts.Repository assistantRepo;
    private final Map<UUID, PendingTurn> pending = new ConcurrentHashMap<>();

    public AgentController(WebAgentService agentService, SseHub sseHub, AssistantPorts.Repository assistantRepo) {
        this.agentService = agentService;
        this.sseHub = sseHub;
        this.assistantRepo = assistantRepo;
    }

    /** Send a message, returns turnId and sessionId. The client subscribes to SSE for results. */
    @PostMapping("/turn")
    public ResponseEntity<TurnResponse> turn(@RequestBody @Valid TurnRequest req) {
        // Get or create session — title auto-generated from first message
        UUID sessionId = req.sessionId() != null ? req.sessionId() :
                assistantRepo.createSession(sessionTitle(req.message())).id();

        AgentRequest agentReq = new AgentRequest(req.message(), false, req.context(), null, sessionId);
        AgentResponse agentResp = agentService.handle(agentReq);
        UUID turnId = agentResp.turnId();

        if (agentResp.confirmationRequired()) {
            pending.put(turnId, new PendingTurn(req.message(), req.context(), sessionId));
        }

        // Publish the agent response event via SSE
        sseHub.publishToTurn(turnId.toString(), "turn_result", Map.of(
                "turnId", turnId.toString(),
                "sessionId", sessionId.toString(),
                "message", agentResp.message(),
                "tools", agentResp.tools(),
                "confirmationRequired", agentResp.confirmationRequired(),
                "result", agentResp.result()
        ));

        TurnResponse resp = new TurnResponse(
                turnId.toString(), sessionId.toString(),
                agentResp.message(), agentResp.tools(), agentResp.confirmationRequired());
        return ResponseEntity.accepted().body(resp);
    }

    /** Subscribe to SSE events for a specific turn. */
    @GetMapping(value = "/turns/{turnId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String turnId) {
        return sseHub.subscribe(turnId);
    }

    /** Confirm a high-risk action. Re-executes the turn with confirmed=true. */
    @PostMapping("/turns/{turnId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable String turnId, @RequestBody ConfirmRequest req) {
        UUID tid = UUID.fromString(turnId);
        PendingTurn pendingTurn = pending.remove(tid);
        if (pendingTurn == null) {
            // Already confirmed or expired — notify frontend to reset state
            sseHub.publishToTurn(turnId, "confirmation_expired", Map.of(
                    "turnId", turnId, "message", "该操作已过期或已处理"
            ));
            return ResponseEntity.noContent().build();
        }
        try {
            AgentRequest agentReq = new AgentRequest(
                    pendingTurn.message(), req.approved(), pendingTurn.context(), tid, pendingTurn.sessionId());
            AgentResponse agentResp = agentService.handle(agentReq);

            sseHub.publishToTurn(turnId, "turn_result", Map.of(
                    "turnId", turnId,
                    "sessionId", pendingTurn.sessionId().toString(),
                    "message", agentResp.message(),
                    "tools", agentResp.tools(),
                    "confirmationRequired", false,
                    "result", agentResp.result()
            ));
        } catch (Exception e) {
            sseHub.publishToTurn(turnId, "error", Map.of(
                    "turnId", turnId, "message", "操作失败: " + e.getMessage()
            ));
        }

        return ResponseEntity.noContent().build();
    }

    /** List recent sessions. */
    @GetMapping("/sessions")
    public ResponseEntity<?> sessions(@RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(assistantRepo.listSessions(limit, offset));
    }

    /** Get a single session. */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        var session = assistantRepo.getSession(UUID.fromString(sessionId));
        if (session.isEmpty()) return ResponseEntity.notFound().build();
        var turns = assistantRepo.listTurns(UUID.fromString(sessionId), 1);
        String lastMessage = turns.isEmpty() ? "" : turns.getFirst().content();
        return ResponseEntity.ok(Map.of(
                "session", session.get(),
                "lastMessage", lastMessage.length() > 60 ? lastMessage.substring(0, 60) + "…" : lastMessage));
    }

    /** Update session title. */
    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "title required"));
        assistantRepo.updateSessionTitle(UUID.fromString(sessionId), title);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Archive (delete) a session. */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        assistantRepo.archiveSession(UUID.fromString(sessionId));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Fork a new session from a specific turn, carrying over previous turns as context. */
    @PostMapping("/sessions/{sessionId}/fork")
    public ResponseEntity<?> forkSession(@PathVariable String sessionId,
                                         @RequestParam UUID fromTurn) {
        var turns = assistantRepo.listTurns(UUID.fromString(sessionId), 500);
        // Find all turns up to (and including) fromTurn
        List<Map<String, Object>> history = new ArrayList<>();
        for (var turn : turns) {
            history.add(Map.of("role", turn.role().name(), "content", turn.content()));
            if (turn.id().equals(fromTurn)) break;
        }
        String forkTitle = history.isEmpty() ? "分支对话"
                : sessionTitle(String.valueOf(history.get(history.size()-1).get("content")));
        var newSession = assistantRepo.createSession(forkTitle);
        return ResponseEntity.ok(Map.of(
                "sessionId", newSession.id().toString(),
                "title", newSession.title(),
                "history", history));
    }

    /** Get turns for a session. */
    @GetMapping("/sessions/{sessionId}/turns")
    public ResponseEntity<?> sessionTurns(@PathVariable String sessionId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(assistantRepo.listTurns(UUID.fromString(sessionId), limit));
    }

    /** Generate a concise session title from the user's first message. */
    private static String sessionTitle(String message) {
        if (message == null || message.isBlank()) return "新对话";
        // Strip markdown, newlines, extra whitespace
        String cleaned = message.replaceAll("[*_`#~>\\[\\]]", "")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 30) return cleaned;
        // Truncate at word boundary
        int cut = cleaned.lastIndexOf(' ', 30);
        return (cut > 15 ? cleaned.substring(0, cut) : cleaned.substring(0, 30)) + "…";
    }

    public record TurnRequest(
            @NotBlank String message,
            Map<String, Object> context,
            UUID sessionId) {
        public TurnRequest {
            if (context == null) context = Map.of();
        }
    }

    public record TurnResponse(
            String turnId, String sessionId,
            String message, java.util.List<String> tools, boolean confirmationRequired) {}

    public record ConfirmRequest(boolean approved) {}

    private record PendingTurn(String message, Map<String, Object> context, UUID sessionId) {}
}
