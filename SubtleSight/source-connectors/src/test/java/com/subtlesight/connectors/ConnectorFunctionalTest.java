package com.subtlesight.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.Models.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ConnectorFunctionalTest {
    private HttpServer server;
    private URI base;
    private SafeHttpClient http;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", e -> html(e, "<a href='/article'>Article</a>"));
        server.createContext("/article", e -> text(e, "body", "text/plain"));
        server.createContext("/redirect", e -> { e.getResponseHeaders().add("Location", "/ok"); e.sendResponseHeaders(302, -1); e.close(); });
        server.createContext("/rss", e -> {
            String link = "http://127.0.0.1:" + server.getAddress().getPort() + "/article";
            text(e, "<?xml version='1.0'?><rss version='2.0'><channel><title>T</title><link>" + link + "</link><description>D</description><item><guid>one</guid><title>First</title><link>" + link + "</link><pubDate>Thu, 16 Jul 2026 00:00:00 GMT</pubDate></item></channel></rss>", "application/rss+xml");
        });
        server.createContext("/hn/topstories.json", e -> json(e, "[100,99]"));
        server.createContext("/hn/item/100.json", e -> json(e, "{\"id\":100,\"type\":\"story\",\"by\":\"pg\",\"time\":1784160000,\"title\":\"HN real API shape\",\"url\":\"https://example.org/hn-real\",\"score\":42,\"descendants\":7}"));
        server.createContext("/github/search", e -> json(e, "{\"items\":[{\"full_name\":\"openai/example-agent\",\"html_url\":\"https://github.com/openai/example-agent\",\"description\":\"Agent framework\",\"stargazers_count\":1200,\"forks_count\":80,\"language\":\"TypeScript\",\"created_at\":\"2026-07-16T00:00:00Z\",\"updated_at\":\"2026-07-17T00:00:00Z\",\"pushed_at\":\"2026-07-17T01:00:00Z\",\"owner\":{\"login\":\"openai\"}}]}"));
        server.createContext("/hf/models", e -> json(e, "[{\"modelId\":\"org/model\",\"pipeline_tag\":\"text-generation\",\"downloads\":1234,\"likes\":56,\"trendingScore\":88,\"tags\":[\"llm\",\"agent\"],\"createdAt\":\"2026-07-16T00:00:00Z\",\"lastModified\":\"2026-07-17T00:00:00Z\"}]"));
        server.createContext("/arxiv", e -> {
            String link = "http://127.0.0.1:" + server.getAddress().getPort() + "/abs/1234";
            text(e, "<?xml version='1.0'?><feed xmlns='http://www.w3.org/2005/Atom'><title>arXiv</title><entry><id>" + link + "</id><title>Agent benchmark paper</title><link href='" + link + "'/><summary>Paper abstract.</summary><published>2026-07-16T00:00:00Z</published><author><name>A. Researcher</name></author><category term='cs.AI'/></entry></feed>", "application/atom+xml");
        });
        server.createContext("/producthunt", e -> {
            String link = "http://127.0.0.1:" + server.getAddress().getPort() + "/product";
            text(e, "<?xml version='1.0'?><rss version='2.0'><channel><title>PH</title><link>" + link + "</link><description>D</description><item><guid>ph-one</guid><title>Launch One</title><link>" + link + "</link><description>Launch summary</description><pubDate>Thu, 16 Jul 2026 00:00:00 GMT</pubDate></item></channel></rss>", "application/rss+xml");
        });
        server.createContext("/ia/search", e -> json(e, "{\"response\":{\"docs\":[{\"identifier\":\"video-one\",\"title\":\"Playable public video\",\"description\":\"Public fixture video\",\"publicdate\":\"2026-07-18T03:00:00Z\",\"downloads\":99,\"item_size\":1024}]}}"));
        server.createContext("/metadata/video-one", e -> json(e, "{\"files\":[{\"name\":\"sample.mp4\",\"format\":\"MPEG4\",\"size\":\"12\",\"length\":\"5.0\"},{\"name\":\"sample.thumbs/sample_000001.jpg\",\"format\":\"Thumbnail\",\"size\":\"20\"}] }"));
        server.createContext("/download/video-one/sample.mp4", e -> {
            byte[] body = new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
            e.getResponseHeaders().add("Content-Type", "video/mp4");
            e.sendResponseHeaders(200, body.length);
            e.getResponseBody().write(body);
            e.close();
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        http = new SafeHttpClient(Duration.ofSeconds(2), 2, 4096, u -> {});
    }

    @AfterEach void stop() { server.stop(0); }

    private Source source(SourceType type, String path) {
        Instant now = Instant.now();
        return new Source(UUID.randomUUID(), "Fixture", type, SourceKind.PERSISTENT, base + path, null, null, SourceTier.PRIMARY, SourceHealth.HEALTHY, Set.of(), true, 1, now, now);
    }

    @Test void safeClientFollowsRedirectAndWebConnectorDiscoversAbsoluteLinks() {
        var ref = new SourceConnector.ExternalReference("r", base.resolve("/redirect"), "r", null, Map.of());
        var payload = http.fetch(ref);
        assertThat(payload.status()).isEqualTo(200);
        assertThat(payload.redirectChain()).containsExactly(base.resolve("/redirect"));
        var web = new WebSourceConnector(http, SourceType.WEBSITE);
        var batch = web.discover(source(SourceType.WEBSITE, "/ok"), null);
        assertThat(batch.references()).singleElement().satisfies(r -> assertThat(r.uri()).isEqualTo(base.resolve("/article")));
        assertThat(web.fetch(batch.references().getFirst()).status()).isEqualTo(200);
        assertThat(web.healthCheck(source(SourceType.WEBSITE, "/ok")).healthy()).isTrue();
    }

    @Test void rssSupportsCursorFetchAndHealthAndRegistry() {
        var rss = new RssSourceConnector(http);
        var batch = rss.discover(source(SourceType.RSS, "/rss"), null);
        assertThat(batch.references()).singleElement().satisfies(r -> assertThat(r.externalId()).isEqualTo("one"));
        assertThat(rss.discover(source(SourceType.RSS, "/rss"), "one").references()).isEmpty();
        assertThat(rss.healthCheck(source(SourceType.RSS, "/rss")).healthy()).isTrue();
        assertThat(rss.fetch(batch.references().getFirst()).status()).isEqualTo(200);
        var registry = new ConnectorRegistry().register(rss);
        assertThat(registry.require(SourceType.RSS)).isSameAs(rss);
        assertThatThrownBy(() -> registry.require(SourceType.HN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void genericTypesReturnEndpointAndInvalidFeedsFail() {
        var api = new WebSourceConnector(http, SourceType.CUSTOM_API);
        assertThat(api.discover(source(SourceType.CUSTOM_API, "/ok"), null).references()).hasSize(1);
        assertThatThrownBy(() -> new RssSourceConnector(http).discover(source(SourceType.RSS, "/ok"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new WebSourceConnector(http, SourceType.WEBSITE).healthCheck(source(SourceType.WEBSITE, "/missing")).healthy()).isFalse();
        assertThatCode(() -> http.fetch(new SourceConnector.ExternalReference("x", base.resolve("/missing"), "x", null, Map.of()))).doesNotThrowAnyException();
    }

    @Test void structuredPublicConnectorsCreateIndexableHtmlFromApiPayloads() {
        ObjectMapper json = new ObjectMapper();
        var hn = new HackerNewsSourceConnector(http, json, 1);
        var hnRef = hn.discover(source(SourceType.HN, "/hn/topstories.json"), null).references().getFirst();
        assertThat(new String(hn.fetch(hnRef).content(), StandardCharsets.UTF_8)).contains("HN real API shape", "score");
        var gh = new GitHubTrendingSourceConnector(http, json, 1);
        var ghRef = gh.discover(source(SourceType.GITHUB, "/github/search"), null).references().getFirst();
        assertThat(ghRef.publishedAt()).isEqualTo(Instant.parse("2026-07-17T01:00:00Z"));
        assertThat(new String(gh.fetch(ghRef).content(), StandardCharsets.UTF_8)).contains("openai/example-agent", "stars");
        var hf = new HuggingFaceTrendingSourceConnector(http, json, 1);
        var hfRef = hf.discover(source(SourceType.HUGGING_FACE, "/hf/models"), null).references().getFirst();
        assertThat(new String(hf.fetch(hfRef).content(), StandardCharsets.UTF_8)).contains("org/model", "downloads");
        var arxiv = new ArxivSourceConnector(http, 1);
        var arxivRef = arxiv.discover(source(SourceType.ARXIV, "/arxiv"), null).references().getFirst();
        assertThat(new String(arxiv.fetch(arxivRef).content(), StandardCharsets.UTF_8)).contains("Agent benchmark paper", "Paper abstract");
        var ph = new ProductHuntSourceConnector(http, 1);
        var phRef = ph.discover(source(SourceType.PRODUCT_HUNT, "/producthunt"), null).references().getFirst();
        assertThat(new String(ph.fetch(phRef).content(), StandardCharsets.UTF_8)).contains("Launch One", "Launch summary");
    }

    @Test void internetArchiveVideoConnectorDiscoversMetadataAndDownloadsPlayableVideo() {
        var connector = new InternetArchiveVideoSourceConnector(http, new ObjectMapper(), 2, 4096);
        var batch = connector.discover(source(SourceType.VIDEO, "/ia/search"), null);
        assertThat(batch.references()).singleElement().satisfies(ref -> {
            assertThat(ref.title()).isEqualTo("Playable public video");
            assertThat(ref.publishedAt()).isEqualTo(Instant.parse("2026-07-18T03:00:00Z"));
            assertThat(ref.metadata()).containsEntry("mediaType", "video/mp4").containsEntry("platform", "Internet Archive");
        });
        var payload = connector.fetch(batch.references().getFirst());
        assertThat(payload.status()).isEqualTo(200);
        assertThat(payload.mediaType()).isEqualTo("video/mp4");
        assertThat(payload.content()).hasSize(12);
        assertThat(connector.healthCheck(source(SourceType.VIDEO, "/ia/search")).healthy()).isTrue();
    }

    @Test void openCliVideoConnectorReadsHotJsonAndDownloadedMedia() throws Exception {
        Path fixtureDir = Path.of("target", "opencli-fixture").toAbsolutePath().normalize();
        Files.createDirectories(fixtureDir);
        Path fakeOpenCli = fixtureDir.resolve("opencli.cmd");
        Files.writeString(fakeOpenCli, """
                @echo off
                if "%1"=="--version" (
                  echo opencli-fixture 1.0
                  exit /b 0
                )
                if "%1"=="bilibili" if "%2"=="hot" (
                  echo [{"bvid":"BV1fixture","title":"Fixture Hot Video","url":"https://www.bilibili.com/video/BV1fixture","owner":"Fixture UP","play":12345}]
                  exit /b 0
                )
                if "%1"=="bilibili" if "%2"=="download" (
                  set OUT=
                :loop
                  if "%1"=="" goto done
                  if "%1"=="--output" set OUT=%2
                  shift
                  goto loop
                :done
                  if "%OUT%"=="" exit /b 78
                  echo fixture-video>"%OUT%\\fixture.mp4"
                  exit /b 0
                )
                exit /b 66
                """, StandardCharsets.UTF_8);
        Path downloads = fixtureDir.resolve("downloads");
        var connector = new OpenCliVideoSourceConnector(new ObjectMapper(), fakeOpenCli.toString(), downloads, Duration.ofSeconds(10));
        Instant now = Instant.now();
        var source = new Source(UUID.randomUUID(), "OpenCLI Fixture", SourceType.VIDEO, SourceKind.PERSISTENT, "opencli://bilibili/hot?limit=5&download=true", null, null, SourceTier.SOCIAL, SourceHealth.HEALTHY, Set.of("video"), true, 1, now, now);
        var batch = connector.discover(source, null);
        assertThat(batch.references()).singleElement().satisfies(ref -> {
            assertThat(ref.title()).isEqualTo("Fixture Hot Video");
            assertThat(ref.metadata()).containsEntry("provider", "OpenCLI").containsEntry("platform", "B站").containsEntry("download", "true");
        });
        var payload = connector.fetch(batch.references().getFirst());
        assertThat(payload.status()).isEqualTo(200);
        assertThat(payload.mediaType()).isEqualTo("video/mp4");
        assertThat(new String(payload.content(), StandardCharsets.UTF_8)).contains("fixture-video");
        assertThat(connector.healthCheck(source).healthy()).isTrue();
    }

    private static void json(com.sun.net.httpserver.HttpExchange e, String body) throws java.io.IOException {
        text(e, body, "application/json");
    }

    private static void html(com.sun.net.httpserver.HttpExchange e, String body) throws java.io.IOException {
        text(e, body, "text/html");
    }

    private static void text(com.sun.net.httpserver.HttpExchange e, String body, String contentType) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", contentType);
        e.sendResponseHeaders(200, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }
}
