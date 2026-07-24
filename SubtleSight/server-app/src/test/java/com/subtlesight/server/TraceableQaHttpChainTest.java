package com.subtlesight.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceableQaHttpChainTest {
    private static final Path DATA = Path.of(
            System.getProperty("java.io.tmpdir"), "subtlesight-traceable-http-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("subtlesight.data-dir", DATA::toString);
        registry.add("subtlesight.collect.scheduler.enabled", () -> "false");
        registry.add("subtlesight.calendar.scheduler.enabled", () -> "false");
    }

    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();
    private CookieManager cookies;
    private HttpClient http;
    private URI base;
    private String csrf;

    @BeforeEach
    void startClient() throws Exception {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        base = URI.create("http://127.0.0.1:" + port);
        HttpResponse<String> auth = http.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(auth.statusCode()).isEqualTo(HttpStatus.OK.value());
        csrf = cookies.getCookieStore().get(base).stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }

    @Test
    void documentToVerifiedAnswerToKnowledgeAndDrawIsARealHttpClosedLoop() throws Exception {
        HttpResponse<String> forbidden = rawPost("/api/v1/qa/scopes/resolve", "{\"scopes\":[]}", false);
        assertThat(forbidden.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());

        JsonNode document = post("/api/v1/knowledge/documents", Map.of(
                "title", "Release evidence",
                "contentHtml", "<h2 data-block-id=\"status\">Release status</h2>"
                        + "<p data-block-id=\"cause\">The API freeze moved to Friday and compressed the test window.</p>",
                "drawingJson", "{\"nodes\":[{\"id\":\"timeline\",\"kind\":\"note\","
                        + "\"x\":80,\"y\":120,\"width\":220,\"height\":100,"
                        + "\"label\":\"API freeze moved to Friday\"}],\"edges\":[]}"));
        String documentId = document.path("id").asText();
        assertThat(document.path("version").asInt()).isEqualTo(1);

        JsonNode documentIndex = awaitGet(
                "/api/v1/knowledge/index/status?type=DOCUMENT&id=" + documentId + "&version=1",
                node -> node.path("status").asText().equals("READY"));
        JsonNode drawIndex = awaitGet(
                "/api/v1/knowledge/index/status?type=DRAW_NODE&id=" + documentId + "&version=1",
                node -> node.path("status").asText().equals("READY"));
        assertThat(documentIndex.path("units").asInt()).isEqualTo(2);
        assertThat(drawIndex.path("units").asInt()).isEqualTo(1);

        JsonNode scope = post("/api/v1/qa/scopes/resolve", Map.of("scopes", new Object[]{Map.of(
                "type", "DOCUMENT", "id", documentId)}));
        assertThat(scope.path("resources")).hasSize(1);
        assertThat(scope.path("unitIds")).hasSize(2);

        JsonNode started = post("/api/v1/qa/answers", Map.of(
                "question", "What caused the test window to be compressed?",
                "scopes", new Object[]{Map.of("type", "DOCUMENT", "id", documentId)}));
        String answerId = started.path("answer").path("id").asText();
        assertThat(started.path("answer").path("status").asText()).isEqualTo("QUEUED");
        assertThat(started.path("jobId").asText()).isNotBlank();

        JsonNode answer = awaitGet("/api/v1/qa/answers/" + answerId,
                node -> node.path("answer").path("status").asText().matches("COMPLETED|INSUFFICIENT|FAILED"));
        assertThat(answer.path("answer").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(answer.path("claims")).hasSize(1);
        JsonNode claimView = answer.path("claims").get(0);
        String claimId = claimView.path("claim").path("id").asText();
        String citationId = claimView.path("citations").get(0).path("id").asText();
        assertThat(claimView.path("claim").path("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(claimView.path("citations").get(0).path("exactQuote").asText())
                .isEqualTo(answer.path("answer").path("directAnswer").asText());

        JsonNode citation = get("/api/v1/qa/citations/" + citationId + "/resolve");
        assertThat(citation.path("locatorValid").asBoolean()).isTrue();
        assertThat(citation.path("stale").asBoolean()).isFalse();
        assertThat(citation.path("unit").path("stableLocator").asText()).isEqualTo("block:cause");

        JsonNode followUpStart = post("/api/v1/qa/answers/" + answerId + "/follow-ups",
                Map.of("question", "Which deadline moved?"));
        String followUpId = followUpStart.path("answer").path("id").asText();
        JsonNode followUp = awaitGet("/api/v1/qa/answers/" + followUpId,
                node -> node.path("answer").path("status").asText().matches("COMPLETED|INSUFFICIENT|FAILED"));
        assertThat(followUp.path("answer").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(followUp.path("answer").path("parentAnswerId").asText()).isEqualTo(answerId);
        assertThat(json.readTree(followUp.path("scopeSnapshotJson").asText()).path("unitIds")).hasSize(2);

        JsonNode saved = post("/api/v1/qa/answers/" + answerId + "/save-document",
                Map.of("title", "Verified release conclusion"));
        assertThat(saved.path("title").asText()).isEqualTo("Verified release conclusion");
        assertThat(saved.path("contentHtml").asText()).contains("qa-claim-1", answerId);
        String savedDocumentId = saved.path("id").asText();
        awaitGet("/api/v1/knowledge/index/status?type=DOCUMENT&id=" + savedDocumentId + "&version=1",
                node -> node.path("status").asText().equals("READY"));

        JsonNode updated = post("/api/v1/qa/claims/" + claimId + "/add-to-draw",
                Map.of("documentId", documentId, "expectedVersion", 1));
        assertThat(updated.path("version").asInt()).isEqualTo(2);
        assertThat(updated.path("drawingJson").asText()).contains("qa-" + claimId, citationId, answerId);
        awaitGet("/api/v1/knowledge/index/status?type=DRAW_NODE&id=" + documentId + "&version=2",
                node -> node.path("status").asText().equals("READY"));

        JsonNode staleCitation = get("/api/v1/qa/citations/" + citationId + "/resolve");
        assertThat(staleCitation.path("stale").asBoolean()).isTrue();
        assertThat(staleCitation.path("currentVersion").asText()).isEqualTo("2");
        assertThat(staleCitation.path("locatorValid").asBoolean()).isTrue();

        JsonNode idempotent = post("/api/v1/qa/claims/" + claimId + "/add-to-draw",
                Map.of("documentId", documentId, "expectedVersion", 2));
        assertThat(idempotent.path("version").asInt()).isEqualTo(2);
    }

    private JsonNode awaitGet(String path, Predicate<JsonNode> complete) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = rawGet(path);
            if (response.statusCode() == HttpStatus.OK.value()) {
                latest = json.readTree(response.body());
                if (complete.test(latest)) return latest;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for " + path + "; latest=" + latest);
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = rawGet(path);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(HttpStatus.OK.value());
        return json.readTree(response.body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        HttpResponse<String> response = rawPost(path, json.writeValueAsString(body), true);
        assertThat(response.statusCode()).as(response.body()).isIn(HttpStatus.OK.value(), HttpStatus.ACCEPTED.value());
        return json.readTree(response.body());
    }

    private HttpResponse<String> rawGet(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve(path)).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rawPost(String path, String body, boolean secured) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).timeout(Duration.ofSeconds(5));
        if (secured) request.header("X-XSRF-TOKEN", csrf);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
