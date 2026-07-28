package com.subtlesight.agent.orchestrator;

import com.subtlesight.agent.events.TurnEvent;
import com.subtlesight.agent.events.TurnEventPublisher;
import com.subtlesight.agent.planner.Planner;
import com.subtlesight.agent.policy.ActionPolicy;
import com.subtlesight.agent.policy.PolicyDecision;
import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.application.AssistantPorts;
import com.subtlesight.application.Ports.AiProvider;
import com.subtlesight.application.Ports.AiProvider.AiRequest;
import com.subtlesight.domain.AssistantModels.TurnStatus;
import com.subtlesight.agent.orchestrator.OrchestratorModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * LLM-driven plan→execute→verify orchestrator using the typed {@link ToolRegistry}
 * and {@link Planner}. Publishes lifecycle events via {@link TurnEventPublisher}.
 */
public final class AssistantOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(AssistantOrchestrator.class);

    private final Planner planner;
    private final ToolRegistry registry;
    private final AssistantPorts.Repository repo;
    private final TurnEventPublisher events;
    private final ActionPolicy policy;
    private final AiProvider ai;
    private final ObjectMapper json = new ObjectMapper();

    /** Backward-compatible SSE callback for existing wiring. */
    @FunctionalInterface
    public interface SseCallback {
        void emit(String turnId, String event, Map<String, Object> data);
    }

    public AssistantOrchestrator(Planner planner, ToolRegistry registry,
                                  AssistantPorts.Repository repo,
                                  TurnEventPublisher events, ActionPolicy policy,
                                  AiProvider ai) {
        this.planner = planner;
        this.registry = registry;
        this.repo = repo;
        this.events = events;
        this.policy = policy;
        this.ai = ai;
    }

    /** Convenience constructor with legacy SseCallback + policy. */
    public AssistantOrchestrator(Planner planner, ToolRegistry registry,
                                  AssistantPorts.Repository repo,
                                  SseCallback sse, ActionPolicy policy,
                                  AiProvider ai) {
        this(planner, registry, repo,
                (TurnEventPublisher) event -> sse.emit(event.turnId(), event.type(), event.data()),
                policy, ai);
    }

    /** Full orchestration entry point. */
    public OrchestrationResult orchestrate(UUID turnId, UUID sessionId,
                                            String message, Map<String, Object> context,
                                            boolean confirmed) {
        // 0. planning_started
        publish(TurnEvent.planningStarted(turnId));

        // 1. Plan
        String history = repo != null ? loadHistory(sessionId) : "[]";
        Plan plan = planner.plan(message, context, history);
        if (repo != null) repo.updateTurnStatus(turnId, TurnStatus.PLANNING, write(plan), null);

        List<Map<String, Object>> stepList = plan.steps().stream().<Map<String, Object>>map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ordinal", s.ordinal()); m.put("tool", s.tool()); m.put("description", s.description());
            return m;
        }).toList();
        publish(TurnEvent.planCreated(turnId, plan.steps().size(), plan.rationale(), stepList));

        // 2. Execute steps
        List<ToolExecution> executions = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            // Policy check: deny, confirm, or rate-limit
            PolicyDecision decision = policy.evaluate(step.tool(), step.params());
            if (!decision.allowed()) {
                // Denied — skip step with error
                ToolExecution te = new ToolExecution(step.ordinal(), step.tool(), Map.of(),
                        false, decision.reason());
                executions.add(te);
                publish(TurnEvent.stepCompleted(turnId, step.ordinal(), step.tool(), false,
                        Map.of("error", decision.reason())));
                if (repo != null) repo.appendAudit(turnId, "policy_denied",
                        "{\"tool\":\"" + step.tool() + "\",\"reason\":\"" + decision.reason() + "\"}");
                continue;
            }
            if (decision.confirmationRequired() && !confirmed) {
                if (repo != null) repo.updateTurnStatus(turnId, TurnStatus.AWAITING_CONFIRM,
                        write(plan), writeExecutions(executions));
                if (repo != null) repo.appendAudit(turnId, "awaiting_confirm",
                        "{\"tool\":\"" + step.tool() + "\",\"ordinal\":" + step.ordinal() + "}");
                publish(TurnEvent.confirmationRequired(turnId, step.tool(), step.description()));
                return new OrchestrationResult(turnId, executions,
                        "步骤 " + step.ordinal() + " — " + step.description() + " — 需要你的确认。",
                        true, step.tool());
            }

            // step_started
            publish(TurnEvent.stepStarted(turnId, step.ordinal(), step.tool(), step.description()));
            if (repo != null) repo.updateTurnStatus(turnId, TurnStatus.EXECUTING,
                    write(plan), writeExecutions(executions));

            // Execute
            ToolExecution te;
            Map<String, Object> resolvedParams = resolveParams(step.tool(), step.params(), executions);
            try {
                Map<String, Object> result = registry.execute(
                        step.tool(), mergeContext(context, resolvedParams));
                boolean success = !result.containsKey("error");
                String error = success ? "" : result.get("error").toString();
                if (!success) {
                    LOG.warn("Tool {} failed for turn {} step {}: {} | params={}",
                            step.tool(), turnId, step.ordinal(), error, write(resolvedParams));
                }
                te = new ToolExecution(step.ordinal(), step.tool(), result, success, error);
            } catch (Exception e) {
                LOG.error("Tool {} threw exception for turn {} step {} | params={}",
                        step.tool(), turnId, step.ordinal(), write(resolvedParams), e);
                te = new ToolExecution(step.ordinal(), step.tool(), Map.of(),
                        false, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            executions.add(te);

            // step_completed
            publish(TurnEvent.stepCompleted(turnId, step.ordinal(), step.tool(),
                    te.success(), te.result()));
            if (repo != null) repo.appendAudit(turnId, "tool_call",
                    "{\"tool\":\"" + step.tool() + "\",\"ordinal\":" + step.ordinal()
                            + ",\"success\":" + te.success() + "}");
        }

        // 3. Synthesize — feed tool results back to LLM for a natural-language response
        String synthesis = null;
        if (ai != null && !executions.isEmpty()) {
            synthesis = synthesize(turnId, message, executions);
        }

        // 3b. Backfill created documents with synthesis content
        if (synthesis != null && !synthesis.isBlank()) {
            for (ToolExecution te : executions) {
                if (!te.success()) continue;
                if (te.tool().equals("create_document") || te.tool().equals("create_report")) {
                    Object docId = te.result().get("id");
                    if (docId == null) docId = te.result().get("documentId");
                    if (docId instanceof String id && !id.isBlank()) {
                        try {
                            Map<String, Object> updateParams = Map.of(
                                    "documentId", id,
                                    "content", "<div>" + synthesis.replace("\n", "</div><div>") + "</div>",
                                    "changeSummary", "AI 自动填充内容");
                            Map<String, Object> updateResult = registry.execute("update_document", updateParams);
                            boolean ok = !updateResult.containsKey("error");
                            ToolExecution backfill = new ToolExecution(
                                    executions.size(), "update_document", updateResult, ok,
                                    ok ? "" : String.valueOf(updateResult.get("error")));
                            executions.add(backfill);
                            publish(TurnEvent.stepCompleted(turnId, backfill.ordinal(),
                                    "update_document", ok, updateResult));
                        } catch (Exception e) {
                            LOG.warn("Backfill update_document failed for {}: {}", id, e.getMessage());
                        }
                        break; // Only backfill the first created document
                    }
                }
            }
        }

        // 4. turn_completed
        if (repo != null) repo.updateTurnStatus(turnId, TurnStatus.COMPLETED,
                write(plan), writeExecutions(executions));
        boolean allSuccess = executions.stream().allMatch(ToolExecution::success);
        publish(TurnEvent.turnCompleted(turnId, executions.size(), allSuccess));

        String summary = synthesis != null ? synthesis
                : executions.size() + " 个步骤已执行";
        return new OrchestrationResult(turnId, executions, summary, false, "");
    }

    /** Feed tool execution results back to the LLM for a natural-language summary. */
    private String synthesize(UUID turnId, String userMessage, List<ToolExecution> executions) {
        try {
            StringBuilder results = new StringBuilder();
            for (ToolExecution e : executions) {
                results.append("- ").append(e.tool()).append(": ");
                if (e.success()) {
                    results.append(summarizeResult(e.tool(), e.result()));
                } else {
                    results.append("失败 — ").append(e.error());
                }
                results.append("\n");
            }

            String systemPrompt = """
                    你是 SubtleSight AI 助手。根据用户请求和工具执行结果，生成一段简洁的自然语言回复。
                    规则：
                    - 用中文回复
                    - 如果搜索结果有内容，总结关键发现
                    - 如果创建了文档，说明文档标题和用途
                    - 不要重复"已执行N个步骤"之类的内容
                    - 控制在 3-8 句话以内
                    """;

            String userPrompt = "用户请求: " + userMessage + "\n\n工具执行结果:\n" + results;

            AiProvider.AiResult aiResult = ai.complete(new AiRequest(
                    "synthesize", systemPrompt, userPrompt, null, 600, 0.7));
            String content = aiResult.content();
            if (content != null && !content.isBlank()) {
                publish(TurnEvent.streamingChunk(turnId, content));
                return content;
            }
        } catch (Exception e) {
            LOG.warn("Synthesis failed for turn {}: {}", turnId, e.getMessage());
        }
        return null;
    }

    /** Summarize a single tool result for the synthesis prompt. */
    private String summarizeResult(String tool, Map<String, Object> result) {
        return switch (tool) {
            case "search_local" -> {
                Object hits = result.get("hits");
                if (hits instanceof List<?> list) {
                    StringBuilder sb = new StringBuilder("找到 " + list.size() + " 条结果");
                    int i = 0;
                    for (Object h : list) {
                        if (i++ >= 3) break;
                        if (h instanceof Map<?, ?> m) {
                            String title = m.containsKey("title") ? String.valueOf(m.get("title")) : "?";
                            sb.append(" | ").append(title);
                        }
                    }
                    yield sb.toString();
                }
                yield "搜索完成";
            }
            case "create_document", "create_report" -> {
                Object title = result.get("title");
                Object id = result.get("id");
                yield "已创建" + (title != null ? ": " + title : "")
                        + (id != null ? " (id=" + id + ")" : "");
            }
            case "update_document" -> {
                Object ver = result.get("version");
                yield "文档已更新" + (ver != null ? "至 V" + ver : "");
            }
            case "start_research" -> {
                Object q = result.getOrDefault("question", result.get("topic"));
                yield "研究已启动" + (q != null ? ": " + q : "");
            }
            case "add_watch_target" -> {
                Object name = result.get("name");
                yield "已添加监控" + (name != null ? ": " + name : "");
            }
            case "ask_question" -> {
                Object answer = result.get("answer");
                if (answer instanceof String s && s.length() > 200) {
                    yield "回答: " + s.substring(0, 200) + "...";
                }
                yield answer != null ? "回答: " + answer : "回答完成";
            }
            default -> "执行完成";
        };
    }

    // ── Helpers ──

    private void publish(TurnEvent event) {
        if (events != null) events.publish(event);
    }

    private String loadHistory(UUID sessionId) {
        if (repo == null || sessionId == null) return "[]";
        try {
            var turns = repo.listTurns(sessionId, 10);
            var history = new ArrayList<Map<String, Object>>();
            for (var t : turns) {
                history.add(Map.of("role", t.role().name(), "content", t.content()));
            }
            return json.writeValueAsString(history);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Map<String, Object> mergeContext(Map<String, Object> context, Map<String, Object> params) {
        Map<String, Object> merged = new LinkedHashMap<>(context);
        merged.putAll(params);
        return merged;
    }

    /** Resolve parameter placeholders like ${step1.result.id} using previous step outputs. */
    private Map<String, Object> resolveParams(String toolName, Map<String, Object> params, List<ToolExecution> previous) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            resolved.put(e.getKey(), resolveValue(e.getValue(), previous));
        }
        // Heuristic: draw tools need a documentId; if the planner omitted it, or if the reference could
        // not be resolved (still contains ${), infer from the most recent successful document step.
        if (toolName.startsWith("draw_")) {
            Object rawDocId = resolved.get("documentId");
            boolean needsInference = rawDocId == null
                    || (rawDocId instanceof String s && s.contains("${"));
            if (needsInference) {
                String docId = findLastDocumentId(previous);
                if (docId != null) {
                    LOG.debug("Auto-injecting documentId {} into {}", docId, toolName);
                    resolved.put("documentId", docId);
                }
            }
        }
        return resolved;
    }

    private String findLastDocumentId(List<ToolExecution> previous) {
        for (int i = previous.size() - 1; i >= 0; i--) {
            ToolExecution e = previous.get(i);
            if (!e.success()) continue;
            String tool = e.tool();
            if (tool.equals("create_document") || tool.equals("update_document")
                    || tool.equals("draw_add_node") || tool.equals("draw_add_edge")
                    || tool.equals("draw_update_node") || tool.equals("draw_remove_node")
                    || tool.equals("draw_auto_layout") || tool.equals("draw_diagram")) {
                Object id = e.result().get("documentId");
                if (id instanceof String s && !s.isBlank()) return s;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, List<ToolExecution> previous) {
        if (value instanceof String s) {
            return resolveString(s, previous);
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(e.getKey().toString(), resolveValue(e.getValue(), previous));
            }
            return map;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> resolveValue(v, previous)).toList();
        }
        return value;
    }

    private String resolveString(String s, List<ToolExecution> previous) {
        if (s == null || !s.contains("${")) return s;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int start = s.indexOf("${", i);
            if (start < 0) {
                out.append(s.substring(i));
                break;
            }
            out.append(s.substring(i, start));
            int end = s.indexOf('}', start + 2);
            if (end < 0) {
                out.append(s.substring(start));
                break;
            }
            String ref = s.substring(start + 2, end).trim();
            Object replacement = lookupRef(ref, previous);
            if (replacement == null) {
                LOG.warn("Unresolved step reference in plan param: {}", ref);
                out.append(s, start, end + 1);
            } else {
                out.append(replacement);
            }
            i = end + 1;
        }
        return out.toString();
    }

    private Object lookupRef(String ref, List<ToolExecution> previous) {
        // Supported forms: stepN.result or stepN.result.field
        if (!ref.startsWith("step") || ref.length() <= 4) return null;
        int dot = ref.indexOf('.');
        if (dot < 0) return null;
        int ordinal;
        try {
            ordinal = Integer.parseInt(ref.substring(4, dot));
        } catch (NumberFormatException e) {
            return null;
        }
        ToolExecution exec = previous.stream()
                .filter(e -> e.ordinal() == ordinal)
                .findFirst()
                .orElse(null);
        if (exec == null) return null;
        String rest = ref.substring(dot + 1);
        if (!rest.startsWith("result")) return null;
        if ("result".equals(rest)) return exec.result();
        if (rest.startsWith("result.")) {
            return exec.result().get(rest.substring("result.".length()));
        }
        return null;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { return "{}"; }
    }

    private String writeExecutions(List<ToolExecution> executions) {
        return write(executions.stream().map(e -> Map.of(
                "ordinal", e.ordinal(), "tool", e.tool(),
                "success", e.success(), "error", e.error())).toList());
    }
}
