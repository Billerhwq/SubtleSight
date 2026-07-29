package com.subtlesight.agent.planner;

import com.subtlesight.agent.orchestrator.OrchestratorModels.Plan;
import com.subtlesight.agent.orchestrator.OrchestratorModels.PlanStep;
import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.application.Ports.AiProvider;
import com.subtlesight.application.Ports.AiProvider.AiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * LLM-based planner that uses the configured AI provider to generate multi-step plans.
 * <p>
 * Automatically falls back to the keyword planner when:
 * <ul>
 *   <li>The AI provider is unavailable or disabled</li>
 *   <li>The LLM returns invalid JSON</li>
 *   <li>The user message contains high-risk keywords (safety bypass)</li>
 * </ul>
 */
public final class LlmPlanner implements Planner {

    private final AiProvider ai;
    private final Planner fallback;
    private final PlanPromptBuilder promptBuilder;
    private final Set<String> knownTools;
    private final Set<String> highRiskTools;
    private final ObjectMapper json = new ObjectMapper();

    // High-risk keywords that should bypass LLM and go straight to keyword routing.
    // Note: "delete"/"删除"/"移除" are intentionally NOT bypassed here — the LLM needs to
    // plan a search-before-delete flow. Safety is enforced by ActionPolicy.requireConfirm().
    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
            "修改配置", "覆盖", "修改 provider", "修改 ai",
            "override setting", "modify provider", "modify setting");

    public LlmPlanner(AiProvider ai, Planner fallback, ToolRegistry registry) {
        this.ai = ai;
        this.fallback = fallback;
        this.promptBuilder = new PlanPromptBuilder(registry);
        this.knownTools = registry.toolNames();
        this.highRiskTools = registry.highRiskTools();
    }

    @Override
    public Plan plan(String message, Map<String, Object> context, String history) {
        // Safety: high-risk keywords bypass LLM
        String mLower = message.toLowerCase(Locale.ROOT);
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (mLower.contains(kw.toLowerCase(Locale.ROOT))) {
                return fallback.plan(message, context, history);
            }
        }

        // Multi-step keywords → use fallback for reliable multi-step plans
        if (shouldUseKeywordRouting(message)) return fallback.plan(message, context, history);

        // AI unavailable → fallback
        if (ai == null) return fallback.plan(message, context, history);

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(message, context, history);
            AiProvider.AiResult result = ai.complete(new AiRequest(
                    "plan", systemPrompt, userPrompt, null, 1200, 0.3));

            String content = result.content();
            if (content == null || content.isBlank() || content.contains("\"disabled\"")) {
                return fallback.plan(message, context, history);
            }

            return parseResponse(content, message);
        } catch (Exception e) {
            return fallback.plan(message, context, history);
        }
    }

    // ── Response parsing ──

    Plan parseResponse(String raw, String fallbackMessage) {
        try {
            // Sanity check LLM output for injection markers
            if (com.subtlesight.agent.InjectionDetector.isInjection(raw)) {
                return new Plan(UUID.randomUUID(),
                        List.of(new PlanStep(1, "search_local", fallbackMessage,
                                Map.of("query", fallbackMessage))),
                        "fallback (llm output flagged as injection)");
            }
            String cleaned = extractJson(raw);
            JsonNode root = json.readTree(cleaned);

            // Handle both {"steps":[...]} and [...] formats
            JsonNode stepsNode = root.has("steps") ? root.get("steps") : root;
            if (!stepsNode.isArray()) {
                // Single object → wrap
                stepsNode = json.createArrayNode().add(stepsNode);
            }

            List<PlanStep> steps = new ArrayList<>();
            int ordinal = 1; // Steps are 1-indexed so prompts can reference ${step1.result.id}
            for (JsonNode step : stepsNode) {
                String tool = step.has("tool") ? step.get("tool").asText().trim() : null;
                if (tool == null || (!knownTools.isEmpty() && !knownTools.contains(tool))) {
                    continue; // skip unknown tools
                }

                String desc = step.has("description")
                        ? step.get("description").asText()
                        : "执行 " + tool;

                Map<String, Object> params = new LinkedHashMap<>();
                if (step.has("params") && step.get("params").isObject()) {
                    var iter = step.get("params").fields();
                    while (iter.hasNext()) {
                        var f = iter.next();
                        String key = f.getKey();
                        // Forbidden parameter names
                        if ("systemPrompt".equals(key) || "instructions".equals(key)
                                || "override".equals(key) || "__proto__".equals(key)) {
                            continue; // silently drop forbidden params
                        }
                        params.put(key, nodeToValue(f.getValue()));
                    }
                }
                // Auto-fill default params for tools that need them
                if (params.isEmpty()) {
                    params.putAll(fillDefaultParams(tool, fallbackMessage));
                }

                steps.add(new PlanStep(ordinal++, tool, desc, params));
            }

            if (steps.isEmpty()) {
                steps.add(new PlanStep(1, "search_local", fallbackMessage,
                        Map.of("query", fallbackMessage)));
            }

            String rationale = root.has("rationale") ? root.get("rationale").asText() : "";
            return new Plan(UUID.randomUUID(), steps, rationale);

        } catch (Exception e) {
            return new Plan(UUID.randomUUID(),
                    List.of(new PlanStep(1, "search_local", fallbackMessage,
                            Map.of("query", fallbackMessage))),
                    "fallback (parse error: " + e.getMessage() + ")");
        }
    }

    // ── Helpers ──

    /**
     * Returns true only for simple, single-intent messages where keyword routing
     * is reliable and the LLM adds no value. Everything else (compound tasks,
     * "search then create", research+report, etc.) goes to the LLM.
     */
    static boolean shouldUseKeywordRouting(String message) {
        String m = message.toLowerCase(Locale.ROOT);

        // High-risk ops that don't need search-before-act — keyword planner is sufficient
        // (delete and publish go through LLM for proper planning; safety via ActionPolicy)
        if (m.contains("modify provider") || m.contains("修改配置"))
            return true;

        // Simple, single-intent tool mappings (no compound keywords present)
        boolean isCompound = m.contains("并") || m.contains("然后") || m.contains("接着")
                || m.contains(" and ") || m.contains(" then ")
                || m.contains("先") || m.contains("再") || m.contains("之后");

        if (isCompound) return false; // LLM handles compound tasks

        // Single-intent patterns — keyword planner is sufficient
        if (m.contains("创建文档") || m.contains("新建文档") || m.contains("create document"))
            return true;
        if (m.contains("更新文档") || m.contains("修改文档") || m.contains("update document"))
            return true;
        if (m.contains("监控") || m.contains("monitor") || m.contains("watch") || m.contains("跟踪"))
            return true;
        if (m.contains("文件夹") || m.contains("folder") || m.contains("目录"))
            return true;
        // QA patterns — explicit question-asking intent
        if ((m.contains("有哪些") || m.contains("是什么") || m.contains("怎么样")
                || m.contains("如何") || m.contains("为什么")
                || m.contains("what ") || m.contains("how ") || m.contains("why "))
                && (m.contains("内容") || m.contains("知识") || m.contains("文档") || m.contains("数据")
                || m.contains("content") || m.contains("knowledge") || m.contains("document")))
            return true;

        return false; // Default: let LLM handle
    }

    /** Auto-fill sensible default params for tools with empty params from LLM. */
    static Map<String, Object> fillDefaultParams(String tool, String message) {
        return switch (tool) {
            case "ask_question" -> Map.of("question", message);
            case "search_local", "discover_web" -> Map.of("query", message);
            case "search_documents" -> Map.of("keyword", message);
            case "create_report" -> Map.of("title", message);
            case "create_document" -> Map.of("title", message, "content", "<h2>" + message + "</h2><p></p>");
            case "update_document" -> Map.of("content", "<p>" + message + "</p>", "changeSummary", message);
            case "draw_add_node" -> Map.of("kind", "rect", "label", message);
            case "draw_add_edge" -> Map.of("from", "start", "to", "node-1");
            case "draw_update_node" -> Map.of("label", message);
            case "add_watch_target" -> Map.of("name", message, "expression", message);
            case "delete" -> Map.of("resourceId", message);
            default -> Map.of();
        };
    }

    /** Strip markdown fences and extract pure JSON. */
    static String extractJson(String raw) {
        String s = raw.trim();
        // Remove ```json ... ``` fences
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            } else {
                s = s.replaceAll("```[a-z]*", "").trim();
            }
        }
        return s;
    }

    static Object nodeToValue(JsonNode n) {
        if (n.isTextual()) return n.asText();
        if (n.isInt()) return n.asInt();
        if (n.isLong()) return n.asLong();
        if (n.isDouble() || n.isFloat()) return n.asDouble();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isArray()) {
            List<Object> list = new ArrayList<>();
            n.forEach(child -> list.add(nodeToValue(child)));
            return list;
        }
        if (n.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            n.fields().forEachRemaining(f -> map.put(f.getKey(), nodeToValue(f.getValue())));
            return map;
        }
        return n.asText();
    }
}
