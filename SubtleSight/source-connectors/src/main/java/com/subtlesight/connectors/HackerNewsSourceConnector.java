package com.subtlesight.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HackerNewsSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final ObjectMapper json;
    private final int maxItems;

    public HackerNewsSourceConnector(SafeHttpClient http, ObjectMapper json) {
        this(http, json, 12);
    }

    HackerNewsSourceConnector(SafeHttpClient http, ObjectMapper json, int maxItems) {
        this.http = http;
        this.json = json.copy().findAndRegisterModules();
        this.maxItems = Math.max(1, maxItems);
    }

    @Override public SourceType type() { return SourceType.HN; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        RawPayload payload = http.fetch(new ExternalReference(source.endpoint(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try {
            JsonNode ids = json.readTree(payload.content());
            List<ExternalReference> refs = new ArrayList<>();
            String next = cursor;
            for (JsonNode idNode : ids) {
                String id = idNode.asText();
                if (next == null) next = id;
                if (cursor != null && cursor.equals(id)) break;
                URI itemUri = itemUri(payload.finalUri(), id);
                refs.add(new ExternalReference(id, itemUri, "Hacker News item " + id, null, Map.of("platform","Hacker News")));
                if (refs.size() >= maxItems) break;
            }
            return new DiscoveryBatch(refs, next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Hacker News top stories payload", ex);
        }
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        RawPayload payload = http.fetch(reference);
        try {
            JsonNode item = json.readTree(payload.content());
            String id = item.path("id").asText(reference.externalId());
            String title = item.path("title").asText("Hacker News item " + id);
            String url = item.path("url").asText("https://news.ycombinator.com/item?id=" + id);
            Instant published = HtmlPayloads.epochSeconds(item.path("time").asLong(0));
            Map<String,String> metadata = new LinkedHashMap<>();
            metadata.put("title", title);
            metadata.put("summary", HtmlPayloads.first(item.path("text").asText(""), "HN discussion with score " + item.path("score").asInt(0)));
            metadata.put("by", item.path("by").asText(""));
            metadata.put("score", String.valueOf(item.path("score").asInt(0)));
            metadata.put("comments", String.valueOf(item.path("descendants").asInt(0)));
            metadata.put("discussion", "https://news.ycombinator.com/item?id=" + id);
            ExternalReference decorated = new ExternalReference(id, reference.uri(), title, published, metadata);
            return HtmlPayloads.fromMetadata(decorated, URI.create(url), "Hacker News");
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Hacker News item payload", ex);
        }
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            return new HealthResult(!discover(source, null).references().isEmpty(), (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private static URI itemUri(URI topStories, String id) {
        String path = topStories.getPath();
        int slash = path == null ? -1 : path.lastIndexOf('/');
        String base = slash >= 0 ? path.substring(0, slash) : "";
        return topStories.resolve(base + "/item/" + id + ".json");
    }
}
