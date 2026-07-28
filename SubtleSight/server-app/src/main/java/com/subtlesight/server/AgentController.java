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
        // Get or create session
        UUID sessionId = req.sessionId() != null ? req.sessionId() :
                assistantRepo.createSession("新对话").id();

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
            return ResponseEntity.notFound().build();
        }
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

        return ResponseEntity.noContent().build();
    }

    /** List recent sessions. */
    @GetMapping("/sessions")
    public ResponseEntity<?> sessions(@RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(assistantRepo.listSessions(limit, offset));
    }

    /** Get turns for a session. */
    @GetMapping("/sessions/{sessionId}/turns")
    public ResponseEntity<?> sessionTurns(@PathVariable String sessionId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(assistantRepo.listTurns(UUID.fromString(sessionId), limit));
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
