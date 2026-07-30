package com.subtlesight.agent.planner;

import com.subtlesight.agent.orchestrator.OrchestratorModels.Plan;
import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.ToolParam;
import com.subtlesight.application.Ports.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPlannerTest {

    @Test
    void completesCurrentDocumentWriteAndDiagramWhenLlmOnlyReturnsSearch() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new PlanningTools());
        AiProvider ai = request -> new AiProvider.AiResult(
                "{\"steps\":[{\"tool\":\"search_local\",\"params\":{\"query\":\"AI Agent\"}}]}",
                1, 1, "test", "test");
        LlmPlanner planner = new LlmPlanner(ai, new KeywordPlanner(), registry);

        String documentId = "567466ec-c736-4357-a675-8d5098f42549";
        String message = "在当前空白文档中，从零撰写一篇《AI Agent 的发展现状与落地实践》。"
                + "包含摘要、核心技术、典型应用、主要风险、实施步骤和结论，约 1500 字，"
                + "并将内容直接写入当前文档最后在 Draw 中绘制一张"
                + "“用户请求 → Agent 规划 → 工具执行 → 结果验证 → 最终回答”的流程图。";

        Plan plan = planner.plan(message, Map.of("currentDocId", documentId));

        assertThat(plan.steps()).extracting(step -> step.tool())
                .containsExactly("search_local", "update_document", "draw_diagram");
        assertThat(plan.steps().get(1).params())
                .containsEntry("documentId", documentId)
                .containsEntry("generateFromRequest", true);
        assertThat(plan.steps().get(2).params()).containsEntry("documentId", documentId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) plan.steps().get(2).params().get("nodes");
        assertThat(nodes).extracting(node -> node.get("label"))
                .containsExactly("用户请求", "Agent 规划", "工具执行", "结果验证", "最终回答");
    }

    @Test
    void replacesLlmPlaceholderWithGeneratedCurrentDocumentContent() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new PlanningTools());
        AiProvider ai = request -> new AiProvider.AiResult("""
                {"steps":[{"tool":"update_document","params":{
                  "documentId":"wrong-id","content":"<h2>开始写作</h2><p>占位内容</p>"
                }}]}
                """, 1, 1, "test", "test");
        LlmPlanner planner = new LlmPlanner(ai, new KeywordPlanner(), registry);

        Plan plan = planner.plan("在当前空白文档中，从零撰写一篇约 1500 字的文章",
                Map.of("currentDocId", "current-doc"));

        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).params())
                .containsEntry("documentId", "current-doc")
                .containsEntry("generateFromRequest", true)
                .doesNotContainKey("expectedVersion");
    }

    @Test
    void usesAuthoritativeContextVersionInsteadOfLlmGuess() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new PlanningTools());
        AiProvider ai = request -> new AiProvider.AiResult("""
                {"steps":[{"tool":"update_document","params":{
                  "documentId":"wrong-id","content":"<p>placeholder</p>","expectedVersion":1
                }}]}
                """, 1, 1, "test", "test");
        LlmPlanner planner = new LlmPlanner(ai, new KeywordPlanner(), registry);

        Plan plan = planner.plan("Write an article in the current document",
                Map.of("currentDocId", "current-doc", "currentDocVersion", 9));

        assertThat(plan.steps().getFirst().params())
                .containsEntry("documentId", "current-doc")
                .containsEntry("expectedVersion", 9);
    }

    @Test
    void preservesExplicitNullForStrictOptionalArguments() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new PlanningTools());
        LlmPlanner planner = new LlmPlanner(null, new KeywordPlanner(), registry);

        Plan plan = planner.parseResponse("""
                {"steps":[{"tool":"search_local","params":{"query":"Agent","limit":null}}]}
                """, "搜索 Agent");

        assertThat(plan.steps().getFirst().params())
                .containsEntry("query", "Agent")
                .containsEntry("limit", null);
    }

    public static final class PlanningTools {
        @AgentTool(name = "search_local", description = "搜索")
        public Map<String, Object> search(
                @ToolParam(name = "query", description = "查询") String query,
                @ToolParam(name = "limit", description = "数量", required = false) Integer limit) {
            return Map.of();
        }

        @AgentTool(name = "update_document", description = "更新")
        public Map<String, Object> update(
                @ToolParam(name = "documentId", description = "文档") String documentId,
                @ToolParam(name = "content", description = "正文") String content) {
            return Map.of();
        }

        @AgentTool(name = "draw_diagram", description = "绘图")
        public Map<String, Object> draw(
                @ToolParam(name = "documentId", description = "文档") String documentId,
                @ToolParam(name = "nodes", description = "节点") List<Map<String, Object>> nodes,
                @ToolParam(name = "edges", description = "边") List<Map<String, Object>> edges) {
            return Map.of();
        }
    }
}
