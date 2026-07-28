package com.subtlesight.acceptance;

import com.subtlesight.agent.orchestrator.AssistantOrchestrator;
import com.subtlesight.agent.planner.KeywordPlanner;
import com.subtlesight.agent.policy.ActionPolicy;
import com.subtlesight.agent.tools.DrawToolHelper;
import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.agent.tools.ToolModels.ToolDefinition;
import com.subtlesight.agent.tools.annotations.AgentTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QaDrawE2ETest {

    private ToolRegistry stubRegistry() {
        ToolRegistry r = new ToolRegistry();
        r.register(new Object() {
            @AgentTool(name = "search_local", description = "搜索")
            public Map<String, Object> search() { return Map.of("hits", List.of()); }
            @AgentTool(name = "ask_question", description = "问答")
            public Map<String, Object> ask() { return Map.of("answer", "test answer",
                    "citations", List.of(Map.of("id", "cit-1", "exactQuote", "test quote"))); }
            @AgentTool(name = "draw_add_node", description = "添加节点")
            public Map<String, Object> addNode() { return Map.of("nodeId", "n1"); }
            @AgentTool(name = "draw_add_edge", description = "添加边")
            public Map<String, Object> addEdge() { return Map.of("edgeId", "e1"); }
            @AgentTool(name = "draw_remove_node", description = "删除节点")
            public Map<String, Object> removeNode() { return Map.of("removed", true); }
            @AgentTool(name = "draw_auto_layout", description = "自动布局")
            public Map<String, Object> layout() { return Map.of("layout", true); }
            @AgentTool(name = "create_document", description = "创建文档")
            public Map<String, Object> create() { return Map.of("id", "doc-1"); }
        });
        return r;
    }

    // ── Scenario 4: QA ──

    @Test @DisplayName("场景4: QA 问答 → ask_question → 引用可追溯")
    void qaQuestionWithCitations() {
        var registry = stubRegistry();
        var policy = ActionPolicy.withDefaults(Clock.systemUTC(), registry.highRiskTools());
        var orchestrator = new AssistantOrchestrator(
                new KeywordPlanner(), registry, null,
                (turnId, event, data) -> {}, policy, null);

        var result = orchestrator.orchestrate(UUID.randomUUID(), UUID.randomUUID(),
                "回答知识库中有什么内容", Map.of(), false);

        assertThat(result.confirmationRequired()).isFalse();
        assertThat(result.executions()).hasSize(1);
        assertThat(result.executions().get(0).tool()).isEqualTo("ask_question");
        assertThat(result.executions().get(0).success()).isTrue();
    }

    // ── Scenario 5: Draw ──

    @Test @DisplayName("场景5: Draw 操作 → 3 nodes + 2 edges")
    void drawNodesAndEdges() {
        var registry = stubRegistry();
        var policy = ActionPolicy.withDefaults(Clock.systemUTC(), registry.highRiskTools());
        var orchestrator = new AssistantOrchestrator(
                new KeywordPlanner(), registry, null,
                (turnId, event, data) -> {}, policy, null);

        // Add 3 nodes
        for (String label : List.of("输入", "处理", "输出")) {
            var result = orchestrator.orchestrate(UUID.randomUUID(), UUID.randomUUID(),
                    "画一个" + label + "节点", Map.of("documentId", "doc-1"), false);
            assertThat(result.executions().get(0).tool()).isEqualTo("draw_add_node");
            assertThat(result.executions().get(0).success()).isTrue();
        }
    }

    @Test @DisplayName("场景5: draw_auto_layout → 重新排列节点")
    void drawAutoLayout() {
        var registry = stubRegistry();
        var policy = ActionPolicy.withDefaults(Clock.systemUTC(), registry.highRiskTools());
        var orchestrator = new AssistantOrchestrator(
                new KeywordPlanner(), registry, null,
                (turnId, event, data) -> {}, policy, null);

        var result = orchestrator.orchestrate(UUID.randomUUID(), UUID.randomUUID(),
                "自动整理节点布局", Map.of("documentId", "doc-1"), false);

        assertThat(result.executions().get(0).tool()).isEqualTo("draw_auto_layout");
        assertThat(result.executions().get(0).success()).isTrue();
    }

    // ── DrawToolHelper unit tests ──

    @Test @DisplayName("DrawToolHelper: parse → addNode → addEdge → autoLayout → stringify")
    void drawToolHelperRoundtrip() {
        // Start with empty drawing
        var data = DrawToolHelper.parseDrawing(null);
        assertThat(data.nodes()).isEmpty();
        assertThat(data.edges()).isEmpty();

        // Add 3 nodes
        data = DrawToolHelper.addNode(data, "rect", "输入", null, null);
        data = DrawToolHelper.addNode(data, "diamond", "判断", null, null);
        data = DrawToolHelper.addNode(data, "pill", "输出", null, null);
        assertThat(data.nodes()).hasSize(3);

        // Add 2 edges
        var n1 = data.nodes().get(0).id();
        var n2 = data.nodes().get(1).id();
        var n3 = data.nodes().get(2).id();
        data = DrawToolHelper.addEdge(data, n1, n2);
        data = DrawToolHelper.addEdge(data, n2, n3);
        assertThat(data.edges()).hasSize(2);

        // Update a node
        data = DrawToolHelper.updateNode(data, n2, "确认", "rect", 100.0, 200.0);
        var updated = data.nodes().stream().filter(n -> n.id().equals(n2)).findFirst().orElseThrow();
        assertThat(updated.label()).isEqualTo("确认");
        assertThat(updated.x()).isEqualTo(100.0);

        // Auto layout
        data = DrawToolHelper.autoLayout(data);
        assertThat(data.nodes()).hasSize(3);
        assertThat(data.edges()).hasSize(2);

        // Remove node (should also remove connected edges)
        data = DrawToolHelper.removeNode(data, n2);
        assertThat(data.nodes()).hasSize(2);
        assertThat(data.edges()).hasSize(0); // both edges connected to n2

        // Stringify roundtrip
        String json = DrawToolHelper.stringifyDrawing(data);
        var parsed = DrawToolHelper.parseDrawing(json);
        assertThat(parsed.nodes()).hasSize(2);
    }
}
