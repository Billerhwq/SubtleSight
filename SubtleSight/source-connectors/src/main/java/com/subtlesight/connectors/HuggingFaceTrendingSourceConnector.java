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

public final class HuggingFaceTrendingSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final ObjectMapper json;
    private final int maxItems;

    public HuggingFaceTrendingSourceConnector(SafeHttpClient http, ObjectMapper json) {
        this(http, json, 20);
    }

    HuggingFaceTrendingSourceConnector(SafeHttpClient http, ObjectMapper json, int maxItems) {
        this.http = http;
        this.json = json.copy().findAndRegisterModules();
        this.maxItems = Math.max(1, maxItems);
    }

    @Override public SourceType type() { return SourceType.HUGGING_FACE; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        RawPayload payload = http.fetch(new ExternalReference(source.endpoint(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try {
            JsonNode items = json.readTree(payload.content());
            if (!items.isArray()) throw new IllegalArgumentException("Hugging Face models payload is not an array");
            List<ExternalReference> refs = new ArrayList<>();
            String next = cursor;
            for (JsonNode model : items) {
                String id = HtmlPayloads.first(model.path("modelId").asText(null), model.path("id").asText(null));
                if (id.isBlank()) continue;
                if (next == null) next = id;
                if (cursor != null && cursor.equals(id)) break;
                Map<String,String> metadata = new LinkedHashMap<>();
                metadata.put("title", id);
                metadata.put("summary", "Hugging Face model trending entry: " + id);
                metadata.put("pipeline", model.path("pipeline_tag").asText(""));
                metadata.put("downloads", String.valueOf(model.path("downloads").asLong(0)));
                metadata.put("likes", String.valueOf(model.path("likes").asLong(0)));
                metadata.put("trendingScore", String.valueOf(model.path("trendingScore").asLong(0)));
                metadata.put("tags", join(model.path("tags")));
                metadata.put("lastModified", model.path("lastModified").asText(""));
                refs.add(new ExternalReference(id, URI.create("https://huggingface.co/" + id), id, parseInstant(model.path("createdAt").asText(null)), metadata));
                if (refs.size() >= maxItems) break;
            }
            return new DiscoveryBatch(refs, next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Hugging Face models payload", ex);
        }
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        return HtmlPayloads.fromMetadata(reference, reference.uri(), "Hugging Face");
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            return new HealthResult(!discover(source, null).references().isEmpty(), (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private static String join(JsonNode array) {
        if (!array.isArray()) return "";
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return String.join(", ", values);
    }

    private static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? null : Instant.parse(value); } catch (Exception ignored) { return null; }
    }
}
