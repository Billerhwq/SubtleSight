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

public final class GitHubTrendingSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final ObjectMapper json;
    private final int maxItems;

    public GitHubTrendingSourceConnector(SafeHttpClient http, ObjectMapper json) {
        this(http, json, 20);
    }

    GitHubTrendingSourceConnector(SafeHttpClient http, ObjectMapper json, int maxItems) {
        this.http = http;
        this.json = json.copy().findAndRegisterModules();
        this.maxItems = Math.max(1, maxItems);
    }

    @Override public SourceType type() { return SourceType.GITHUB; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        RawPayload payload = http.fetch(new ExternalReference(source.endpoint(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try {
            JsonNode root = json.readTree(payload.content());
            JsonNode items = root.path("items");
            if (!items.isArray()) throw new IllegalArgumentException(root.path("message").asText("GitHub search payload has no items"));
            List<ExternalReference> refs = new ArrayList<>();
            String next = cursor;
            for (JsonNode repo : items) {
                String fullName = repo.path("full_name").asText();
                if (fullName.isBlank()) continue;
                if (next == null) next = fullName;
                if (cursor != null && cursor.equals(fullName)) break;
                String htmlUrl = repo.path("html_url").asText("https://github.com/" + fullName);
                Instant published = firstInstant(
                        repo.path("pushed_at").asText(null),
                        repo.path("updated_at").asText(null),
                        repo.path("created_at").asText(null));
                Map<String,String> metadata = new LinkedHashMap<>();
                metadata.put("title", fullName);
                metadata.put("summary", repo.path("description").asText(""));
                metadata.put("owner", repo.path("owner").path("login").asText(""));
                metadata.put("stars", String.valueOf(repo.path("stargazers_count").asLong(0)));
                metadata.put("forks", String.valueOf(repo.path("forks_count").asLong(0)));
                metadata.put("language", repo.path("language").asText(""));
                metadata.put("updated", repo.path("updated_at").asText(""));
                metadata.put("pushed", repo.path("pushed_at").asText(""));
                refs.add(new ExternalReference(fullName, URI.create(htmlUrl), fullName, published, metadata));
                if (refs.size() >= maxItems) break;
            }
            return new DiscoveryBatch(refs, next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid GitHub search payload", ex);
        }
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        return HtmlPayloads.fromMetadata(reference, reference.uri(), "GitHub");
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            return new HealthResult(!discover(source, null).references().isEmpty(), (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? null : Instant.parse(value); } catch (Exception ignored) { return null; }
    }

    private static Instant firstInstant(String... values) {
        for (String value : values) {
            Instant parsed = parseInstant(value);
            if (parsed != null) return parsed;
        }
        return null;
    }
}
