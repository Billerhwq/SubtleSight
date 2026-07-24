package com.subtlesight.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeEditorFunctionalTest {
    static final Path DATA = Path.of(
            System.getProperty("java.io.tmpdir"),
            "subtlesight-knowledge-editor-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("subtlesight.data-dir", DATA::toString);
        registry.add("subtlesight.collect-scheduler.enabled", () -> "false");
        registry.add("subtlesight.calendar-scheduler.enabled", () -> "false");
    }

    private final MockMvc mvc;
    private final ObjectMapper json;

    @Autowired
    KnowledgeEditorFunctionalTest(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    @Test
    void documentDrawHistoryAiAndRestoreFormAClosedLoop() throws Exception {
        String drawingV1 = """
                {"nodes":[{"id":"start","kind":"pill","x":50,"y":80,"width":140,"height":70,"label":"开始"}],"edges":[]}
                """;
        String createBody = json.writeValueAsString(Map.of(
                "title", "课程项目说明",
                "contentHtml", "<h2>项目目标</h2><p>完成知识库编辑闭环。</p>",
                "drawingJson", drawingV1));

        String createdPayload = mvc.perform(post("/api/v1/knowledge/documents")
                        .with(csrf())
                        .header("Origin", "http://127.0.0.1:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("课程项目说明"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = json.readTree(createdPayload);
        String id = created.path("id").asText();
        String drawingV2 = """
                {"nodes":[
                  {"id":"start","kind":"pill","x":50,"y":80,"width":140,"height":70,"label":"开始"},
                  {"id":"finish","kind":"accent","x":300,"y":80,"width":140,"height":70,"label":"完成"}
                ],"edges":[{"id":"e1","from":"start","to":"finish"}]}
                """;
        String updateBody = json.writeValueAsString(Map.of(
                "title", "课程项目说明（已完善）",
                "contentHtml", "<h2>项目目标</h2><p>正文已编辑并等待自动保存。</p>",
                "drawingJson", drawingV2,
                "expectedVersion", 1,
                "changeSummary", "同步 Draw 图形"));

        mvc.perform(put("/api/v1/knowledge/documents/{id}", id)
                        .with(csrf())
                        .header("Origin", "http://127.0.0.1:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drawingJson").value(org.hamcrest.Matchers.containsString("\"finish\"")));

        mvc.perform(get("/api/v1/knowledge/documents/{id}/versions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].changeSummary").value("同步 Draw 图形"))
                .andExpect(jsonPath("$[1].version").value(1));

        mvc.perform(put("/api/v1/knowledge/documents/{id}", id)
                        .with(csrf())
                        .header("Origin", "http://127.0.0.1:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isConflict());

        String aiPayload = mvc.perform(post("/api/v1/knowledge/documents/{id}/ai-assist", id)
                        .with(csrf())
                        .header("Origin", "http://127.0.0.1:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"补充验收标准\",\"selectedText\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(aiPayload).path("provider").asText()).isNotBlank();

        mvc.perform(post("/api/v1/knowledge/documents/{id}/versions/1/restore", id)
                        .with(csrf())
                        .header("Origin", "http://127.0.0.1:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.title").value("课程项目说明"))
                .andExpect(jsonPath("$.drawingJson").value(org.hamcrest.Matchers.containsString("\"start\"")));
    }
}
