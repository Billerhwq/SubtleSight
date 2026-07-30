package com.subtlesight.agent.orchestrator;

import com.subtlesight.agent.orchestrator.OrchestratorModels.OrchestrationResult;
import com.subtlesight.agent.orchestrator.OrchestratorModels.Plan;
import com.subtlesight.agent.orchestrator.OrchestratorModels.PlanStep;
import com.subtlesight.agent.planner.Planner;
import com.subtlesight.agent.policy.ActionPolicy;
import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.ToolParam;
import com.subtlesight.application.Ports.AiProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantOrchestratorTest {

    @Test
    void generatesHtmlBeforeUpdatingCurrentDocument() {
        AtomicReference<String> savedContent = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new UpdateTool(savedContent, false));
        AiProvider ai = request -> switch (request.purpose()) {
            case "document_content" -> new AiProvider.AiResult(
                    "```html\n<h1>AI Agent</h1><p>完整正文</p>\n```", 1, 1, "test", "test");
            case "synthesize" -> new AiProvider.AiResult("已写入当前文档。", 1, 1, "test", "test");
            default -> throw new IllegalArgumentException(request.purpose());
        };

        OrchestrationResult result = orchestrator(registry, ai).orchestrate(
                UUID.randomUUID(), UUID.randomUUID(), "在当前文档撰写一篇文章",
                Map.of("currentDocId", "doc-1"), false);

        assertThat(result.executions()).hasSize(1);
        assertThat(result.executions().get(0).success()).isTrue();
        assertThat(savedContent.get()).isEqualTo("<h1>AI Agent</h1><p>完整正文</p>");
        assertThat(result.summary()).contains("已写入当前文档");
    }

    @Test
    void refusesSuccessClaimWhenDocumentUpdateFails() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new UpdateTool(new AtomicReference<>(), true));
        AiProvider ai = request -> switch (request.purpose()) {
            case "document_content" -> new AiProvider.AiResult("<p>正文</p>", 1, 1, "test", "test");
            case "synthesize" -> new AiProvider.AiResult("已写入当前文档。", 1, 1, "test", "test");
            default -> throw new IllegalArgumentException(request.purpose());
        };

        OrchestrationResult result = orchestrator(registry, ai).orchestrate(
                UUID.randomUUID(), UUID.randomUUID(), "在当前文档撰写一篇文章",
                Map.of("currentDocId", "doc-1"), false);

        assertThat(result.executions().get(0).success()).isFalse();
        assertThat(result.summary()).contains("文档正文未能写入").doesNotContain("已写入当前文档");
    }

    @Test
    void rejectsGeneratedContentFarBelowRequestedLength() {
        ToolRegistry registry = new ToolRegistry();
        AtomicReference<String> savedContent = new AtomicReference<>();
        registry.register(new UpdateTool(savedContent, false));
        AiProvider ai = request -> switch (request.purpose()) {
            case "document_content" -> new AiProvider.AiResult("<p>过短正文</p>", 1, 1, "test", "test");
            case "synthesize" -> new AiProvider.AiResult("已写入当前文档。", 1, 1, "test", "test");
            default -> throw new IllegalArgumentException(request.purpose());
        };

        OrchestrationResult result = orchestrator(registry, ai).orchestrate(
                UUID.randomUUID(), UUID.randomUUID(), "在当前文档撰写约 1500 字的文章",
                Map.of("currentDocId", "doc-1"), false);

        assertThat(result.executions().get(0).success()).isFalse();
        assertThat(result.executions().get(0).error()).contains("生成正文长度不足");
        assertThat(savedContent.get()).isNull();
        assertThat(result.summary()).contains("文档正文未能写入");
    }

    private AssistantOrchestrator orchestrator(ToolRegistry registry, AiProvider ai) {
        Planner planner = (message, context, history) -> new Plan(UUID.randomUUID(),
                java.util.List.of(new PlanStep(1, "update_document", "撰写并写入当前文档", Map.of(
                        "documentId", "doc-1",
                        "generateFromRequest", true,
                        "changeSummary", "AI 撰写"))), "test");
        return new AssistantOrchestrator(planner, registry, null,
                (turnId, event, data) -> {},
                ActionPolicy.withDefaults(Clock.systemUTC(), registry.highRiskTools()), ai);
    }

    public static final class UpdateTool {
        private final AtomicReference<String> content;
        private final boolean fail;

        UpdateTool(AtomicReference<String> content, boolean fail) {
            this.content = content;
            this.fail = fail;
        }

        @AgentTool(name = "update_document", description = "更新")
        public Map<String, Object> update(
                @ToolParam(name = "documentId", description = "文档") String documentId,
                @ToolParam(name = "content", description = "正文") String html,
                @ToolParam(name = "changeSummary", description = "说明", required = false) String summary) {
            content.set(html);
            return fail ? Map.of("error", "模拟写入失败")
                    : Map.of("id", documentId, "version", 2);
        }
    }
}
