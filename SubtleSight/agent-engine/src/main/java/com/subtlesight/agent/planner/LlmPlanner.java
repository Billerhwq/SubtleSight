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

            return parseResponse(content, message, context);
        } catch (Exception e) {
            return fallback.plan(message, context, history);
        }
    }

    // ── Response parsing ──

    Plan parseResponse(String raw, String fallbackMessage) {
        return parseResponse(raw, fallbackMessage, Map.of());
    }

    Plan parseResponse(String raw, String fallbackMessage, Map<String, Object> context) {
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

            steps = completeRequiredActions(steps, fallbackMessage, context);
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
     * LLM plans are advisory. Explicit write/draw requests must not disappear just
     * because the model returned a syntactically valid but incomplete plan.
     */
    private List<PlanStep> completeRequiredActions(List<PlanStep> parsed,
                                                   String message,
                                                   Map<String, Object> context) {
        List<PlanStep> completed = new ArrayList<>(parsed);
        String lower = message.toLowerCase(Locale.ROOT);
        String documentId = contextString(context, "currentDocId", "documentId");
        Integer documentVersion = contextInteger(context, "currentDocVersion", "expectedVersion", "version");

        boolean writesCurrentDocument = documentId != null
                && hasAny(lower, "当前文档", "当前空白文档", "此文档", "本篇文档", "current document")
                && hasAny(lower, "撰写", "写一篇", "写入", "直接写", "生成文章", "编辑", "补充", "write");
        boolean generatesDocumentContent = hasAny(lower, "从零撰写", "撰写一篇", "写一篇", "生成一篇",
                "生成文章", "约 1500 字", "约1500字", "write an article");

        if (writesCurrentDocument && generatesDocumentContent) {
            List<PlanStep> normalized = new ArrayList<>(completed.size());
            boolean markedFirstUpdate = false;
            for (PlanStep step : completed) {
                if (step.tool().equals("update_document") && !markedFirstUpdate) {
                    Map<String, Object> params = new LinkedHashMap<>(step.params());
                    params.put("documentId", documentId);
                    params.put("generateFromRequest", true);
                    params.put("changeSummary", "AI 根据用户要求撰写文档");
                    if (documentVersion == null) {
                        params.remove("expectedVersion");
                    } else {
                        params.put("expectedVersion", documentVersion);
                    }
                    normalized.add(new PlanStep(step.ordinal(), step.tool(), step.description(), params));
                    markedFirstUpdate = true;
                } else {
                    normalized.add(step);
                }
            }
            completed = normalized;
        }
        boolean hasDocumentWrite = completed.stream().anyMatch(step -> step.tool().equals("update_document"));

        if (writesCurrentDocument && !hasDocumentWrite && knownTools.contains("update_document")) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("documentId", documentId);
            params.put("generateFromRequest", true);
            params.put("changeSummary", "AI 根据用户要求撰写文档");
            if (documentVersion != null) params.put("expectedVersion", documentVersion);
            completed.add(new PlanStep(completed.size() + 1, "update_document",
                    "撰写内容并写入当前文档", params));
        }

        boolean requestsDiagram = hasAny(lower, "draw", "绘制", "画一张", "画一个", "流程图",
                "架构图", "路线图", "diagram", "→", "->");
        boolean hasDraw = completed.stream().anyMatch(step -> step.tool().startsWith("draw_"));
        if (requestsDiagram && !hasDraw && knownTools.contains("draw_diagram")) {
            Map<String, Object> params = diagramParams(message, documentId);
            completed.add(new PlanStep(completed.size() + 1, "draw_diagram",
                    "在 Draw 中绘制流程图", params));
        }

        // Normalize any draw_diagram step whose nodes may lack the 'id' key
        // (LLMs often omit it), so they pass JSON Schema validation.
        List<PlanStep> normalized2 = new ArrayList<>(completed.size());
        for (PlanStep step : completed) {
            if ("draw_diagram".equals(step.tool())) {
                normalized2.add(new PlanStep(step.ordinal(), step.tool(), step.description(),
                        ensureDrawNodeIds(step.params())));
            } else {
                normalized2.add(step);
            }
        }
        completed = normalized2;

        List<PlanStep> renumbered = new ArrayList<>(completed.size());
        for (int i = 0; i < completed.size(); i++) {
            PlanStep step = completed.get(i);
            renumbered.add(new PlanStep(i + 1, step.tool(), step.description(), step.params()));
        }
        return renumbered;
    }

    private static Map<String, Object> diagramParams(String message, String documentId) {
        List<String> labels = extractArrowLabels(message);
        if (labels.size() < 2) {
            labels = List.of("用户请求", "Agent 规划", "工具执行", "结果验证", "最终回答");
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", null);
            node.put("kind", i == 0 || i == labels.size() - 1 ? "accent" : "rect");
            node.put("label", labels.get(i));
            node.put("x", null);
            node.put("y", null);
            nodes.add(node);
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (int i = 0; i < labels.size() - 1; i++) {
            edges.add(Map.of("from", i, "to", i + 1));
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (documentId != null) params.put("documentId", documentId);
        params.put("nodes", nodes);
        params.put("edges", edges);
        params.put("autoLayout", true);
        return params;
    }

    /** Ensure every node in a draw_diagram params has an {@code id} key
     * so the JSON Schema required-field validation passes. Missing keys
     * are set to {@code null} — the tool auto-generates real IDs. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureDrawNodeIds(Map<String, Object> params) {
        Object rawNodes = params.get("nodes");
        if (!(rawNodes instanceof List<?> list)) return params;
        List<Map<String, Object>> fixedNodes = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> node = new LinkedHashMap<>((Map<String, Object>) m);
                if (!node.containsKey("id")) {
                    node.put("id", null);
                }
                fixedNodes.add(node);
            }
        }
        Map<String, Object> fixed = new LinkedHashMap<>(params);
        fixed.put("nodes", fixedNodes);
        return fixed;
    }

    private static List<String> extractArrowLabels(String message) {
        int arrow = message.indexOf('→');
        if (arrow < 0) arrow = message.indexOf("->");
        String flow = message;
        if (arrow >= 0) {
            int open = Math.max(message.lastIndexOf('“', arrow), message.lastIndexOf('"', arrow));
            int closeCurly = message.indexOf('”', arrow);
            int closeStraight = message.indexOf('"', arrow);
            int close = closeCurly >= 0 && closeStraight >= 0
                    ? Math.min(closeCurly, closeStraight) : Math.max(closeCurly, closeStraight);
            if (open >= 0 && close > open) flow = message.substring(open + 1, close);
        }
        String[] candidates = flow.split("\\s*(?:→|->)\\s*");
        if (candidates.length < 2) return List.of();

        List<String> labels = new ArrayList<>();
        for (String candidate : candidates) {
            String label = candidate.replaceAll("^[\\s\\\"'“”‘’（(]+|[\\s\\\"'“”‘’）),，。；;]+$", "").trim();
            int lastQuote = Math.max(label.lastIndexOf('“'), label.lastIndexOf('"'));
            if (lastQuote >= 0 && lastQuote + 1 < label.length()) label = label.substring(lastQuote + 1).trim();
            if (label.length() > 24) label = label.substring(Math.max(0, label.length() - 24)).trim();
            if (!label.isBlank()) labels.add(label);
        }
        return labels.size() >= 2 && labels.size() <= 10 ? labels : List.of();
    }

    private static String contextString(Map<String, Object> context, String... keys) {
        if (context == null) return null;
        for (String key : keys) {
            Object value = context.get(key);
            if (value instanceof String text && !text.isBlank()) return text;
        }
        return null;
    }

    private static Integer contextInteger(Map<String, Object> context, String... keys) {
        if (context == null) return null;
        for (String key : keys) {
            Object value = context.get(key);
            if (value instanceof Number number) return number.intValue();
            if (value instanceof String text) {
                try { return Integer.parseInt(text); }
                catch (NumberFormatException ignored) { /* try the next key */ }
            }
        }
        return null;
    }

    private static boolean hasAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

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
        if (n.isNull()) return null;
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
