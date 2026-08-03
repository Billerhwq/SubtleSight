package com.subtlesight.agent.orchestrator;

import com.subtlesight.agent.orchestrator.OrchestratorModels.CitationReference;
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
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void anchorsValidMarkersAndStripsUnfoundedOnes() {
        Map<Integer, CitationReference> registry = Map.of(1,
                new CitationReference(1, "story-1", "STORY", "故事一",
                        "https://example.com/1", "", "", "{}", ""));
        var anchored = AssistantOrchestrator.anchorReferences(
                "结论[1]，另外[9]也很重要，来源：某网站", registry);
        assertThat(anchored.answer()).isEqualTo("结论[1]，另外也很重要");
        assertThat(anchored.references()).hasSize(1);
        assertThat(anchored.references().get(0).index()).isEqualTo(1);
        assertThat(anchored.references().get(0).resourceId()).isEqualTo("story-1");
    }

    @Test
    void anchorStripsStandaloneSourceLines() {
        Map<Integer, CitationReference> registry = Map.of(2,
                new CitationReference(2, "doc-1", "DOCUMENT", "文档一", "", "", "", "{}", ""));
        var anchored = AssistantOrchestrator.anchorReferences(
                "要点[2]。\n来源：某网站\n参考：某某\n", registry);
        assertThat(anchored.answer()).isEqualTo("要点[2]。");
        assertThat(anchored.references()).hasSize(1);
    }

    @Test
    void singleSearchStepStripsUnfoundedMarkerThroughOrchestrator() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SearchTool());
        OrchestrationResult result = searchOrchestrator(registry,
                List.of(new PlanStep(1, "search_local", "搜索甲", Map.of("query", "甲"))),
                "结论[1]，另外[9]也很重要，来源：某网站")
                .orchestrate(UUID.randomUUID(), UUID.randomUUID(), "搜索甲", Map.of(), false);
        assertThat(result.summary()).isEqualTo("结论[1]，另外也很重要");
        assertThat(result.references()).hasSize(1);
        assertThat(result.references().get(0).index()).isEqualTo(1);
    }

    @Test
    void globalRekeyingAcrossTwoSearchSteps() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SearchTool());
        OrchestrationResult result = searchOrchestrator(registry,
                List.of(
                        new PlanStep(1, "search_local", "搜索甲", Map.of("query", "甲")),
                        new PlanStep(2, "search_local", "搜索乙", Map.of("query", "乙"))),
                "甲[1]与乙[3]")
                .orchestrate(UUID.randomUUID(), UUID.randomUUID(), "搜索甲", Map.of(), false);
        assertThat(result.summary()).contains("[1]").contains("[3]");
        assertThat(result.references()).hasSize(2);
        assertThat(result.references().get(0).index()).isEqualTo(1);
        assertThat(result.references().get(0).resourceId()).isEqualTo("story-a");
        assertThat(result.references().get(1).index()).isEqualTo(3);
        assertThat(result.references().get(1).resourceId()).isEqualTo("story-b");
    }

    private AssistantOrchestrator searchOrchestrator(ToolRegistry registry,
                                                     List<PlanStep> steps, String answer) {
        AiProvider ai = request -> {
            if (!"synthesize".equals(request.purpose())) {
                throw new IllegalArgumentException(request.purpose());
            }
            return new AiProvider.AiResult(answer, 1, 1, "test", "test");
        };
        Planner planner = (message, context, history) ->
                new Plan(UUID.randomUUID(), steps, "搜索测试");
        return new AssistantOrchestrator(planner, registry, null,
                (turnId, event, data) -> {},
                ActionPolicy.withDefaults(Clock.systemUTC(), registry.highRiskTools()), ai);
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

    /** Fake search_local that returns enriched citationMap; "甲" vs other queries differ. */
    public static final class SearchTool {
        @AgentTool(name = "search_local", description = "搜索本地")
        public Map<String, Object> search(@ToolParam(name = "query", description = "关键词") String query) {
            boolean a = query != null && query.contains("甲");
            String id1 = a ? "story-a" : "story-b";
            String id2 = a ? "doc-a" : "doc-b";
            Map<Integer, Map<String, Object>> cm = new LinkedHashMap<>();
            cm.put(1, meta(id1, "STORY", id1, "https://example.com/" + id1, "2026-01-01T00:00:00Z", "摘要一"));
            cm.put(2, meta(id2, "DOCUMENTVERSION", id2, "https://example.com/" + id2, "2026-02-01T00:00:00Z", "摘要二"));
            return Map.of(
                    "hits", List.of(
                            Map.of("id", id1, "type", "story", "title", id1, "snippet", "…", "score", 1.0),
                            Map.of("id", id2, "type", "document", "title", id2, "snippet", "…", "score", 0.9)),
                    "citationMap", cm);
        }

        private static Map<String, Object> meta(String id, String type, String name,
                                                String url, String publishedAt, String summary) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resourceId", id);
            m.put("resourceType", type);
            m.put("locatorJson", "{}");
            m.put("resourceName", name);
            m.put("url", url);
            m.put("publishedAt", publishedAt);
            m.put("summary", summary);
            m.put("exactQuote", "");
            return m;
        }
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
