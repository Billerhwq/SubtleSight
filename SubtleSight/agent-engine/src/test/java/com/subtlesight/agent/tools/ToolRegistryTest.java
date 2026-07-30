package com.subtlesight.agent.tools;

import com.subtlesight.agent.tools.ToolModels.ResourceRef;
import com.subtlesight.agent.tools.ToolModels.ToolCall;
import com.subtlesight.agent.tools.ToolModels.ToolResult;
import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.ToolEffectSpec;
import com.subtlesight.agent.tools.annotations.ToolParam;
import com.subtlesight.agent.tools.annotations.ToolPresentation;
import com.subtlesight.agent.tools.annotations.ToolRuntime;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void exportsProviderNeutralDefinitionAndProviderAdapters() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new DocumentTool());

        Map<String, Object> definition = registry.definitions().getFirst();
        assertThat(definition)
                .containsEntry("protocolVersion", "1.0")
                .containsEntry("id", "knowledge.document.update")
                .containsEntry("name", "update_document")
                .containsEntry("strict", true)
                .doesNotContainKey("model");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) definition.get("inputSchema");
        assertThat(inputSchema).containsEntry("additionalProperties", false);
        assertThat(inputSchema.get("required"))
                .isEqualTo(java.util.List.of("documentId", "content", "expectedVersion"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> optionalVersion = (Map<String, Object>) properties.get("expectedVersion");
        assertThat(optionalVersion.get("type")).isEqualTo(java.util.List.of("integer", "null"));

        assertThat(registry.functionSchemas().getFirst())
                .containsKeys("type", "name", "description", "parameters", "strict")
                .doesNotContainKey("input_schema");
        assertThat(registry.anthropicSchemas().getFirst())
                .containsKeys("name", "description", "input_schema", "strict")
                .doesNotContainKey("parameters");
    }

    @Test
    void returnsVerifiedEffectAndReusesIdempotentResult() {
        ToolRegistry registry = new ToolRegistry();
        DocumentTool tool = new DocumentTool();
        registry.register(tool);

        ToolCall call = new ToolCall(
                ToolModels.PROTOCOL_VERSION,
                "call_test_123",
                "knowledge.document.update",
                "update_document",
                "1.0.0",
                Map.of("documentId", "doc-1", "content", "<p>正文</p>"),
                new ResourceRef("knowledge_document", "doc-1", 5),
                Map.of("untrustedPageValue", "must-not-become-an-argument"),
                "idem-test-123");

        ToolResult first = registry.execute(call);
        ToolResult second = registry.execute(call);

        assertThat(first.succeeded()).isTrue();
        assertThat(second).isSameAs(first);
        assertThat(tool.invocations).hasValue(1);
        assertThat(first.effects()).singleElement().satisfies(effect -> {
            assertThat(effect.resource().id()).isEqualTo("doc-1");
            assertThat(effect.versionBefore()).isEqualTo(5);
            assertThat(effect.versionAfter()).isEqualTo(6);
            assertThat(effect.verified()).isTrue();
        });
    }

    static final class DocumentTool {
        private final AtomicInteger invocations = new AtomicInteger();

        @AgentTool(id = "knowledge.document.update", name = "update_document",
                description = "更新已有知识文档正文，并返回持久化后的资源版本。",
                risk = AgentTool.RiskLevel.MEDIUM)
        @ToolRuntime(sideEffect = ToolRuntime.SideEffect.WRITE,
                permissions = "knowledge.document.write",
                idempotency = ToolRuntime.Idempotency.REQUIRED,
                idempotencyTtlSeconds = 60,
                concurrency = ToolRuntime.Concurrency.SERIAL_PER_RESOURCE)
        @ToolEffectSpec(type = ToolEffectSpec.Type.RESOURCE_UPDATED,
                resourceType = "knowledge_document", changedFields = "contentHtml")
        @ToolPresentation(label = "更新文档", category = "知识库", icon = "file-pen-line")
        public Map<String, Object> update(
                @ToolParam(name = "documentId", description = "文档 ID") String documentId,
                @ToolParam(name = "content", description = "HTML 正文") String content,
                @ToolParam(name = "expectedVersion", description = "期望版本", required = false)
                Integer expectedVersion) {
            invocations.incrementAndGet();
            return Map.of("id", documentId, "version", 6, "previousVersion", 5);
        }
    }
}
