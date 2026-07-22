package com.subtlesight.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Public-video connector for archive.org advanced search + item metadata + downloadable media files. */
public final class InternetArchiveVideoSourceConnector implements SourceConnector {
    private static final long DEFAULT_MAX_VIDEO_BYTES = 50L * 1024L * 1024L;
    private final SafeHttpClient http;
    private final ObjectMapper json;
    private final int maxMetadataItems;
    private final long maxVideoBytes;

    public InternetArchiveVideoSourceConnector(SafeHttpClient http, ObjectMapper json) {
        this(http, json, 12, DEFAULT_MAX_VIDEO_BYTES);
    }

    public InternetArchiveVideoSourceConnector(SafeHttpClient http, ObjectMapper json, int maxMetadataItems) {
        this(http, json, maxMetadataItems, DEFAULT_MAX_VIDEO_BYTES);
    }

    public InternetArchiveVideoSourceConnector(SafeHttpClient http, ObjectMapper json, int maxMetadataItems, long maxVideoBytes) {
        this.http = http;
        this.json = json;
        this.maxMetadataItems = Math.max(1, maxMetadataItems);
        this.maxVideoBytes = Math.max(1, maxVideoBytes);
    }

    @Override public SourceType type() { return SourceType.VIDEO; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        URI searchUri = URI.create(source.endpoint());
        URI base = origin(searchUri);
        RawPayload payload = http.fetch(new ExternalReference(source.id().toString(), searchUri, source.name(), null, Map.of("platform", "Internet Archive")));
        try {
            JsonNode docs = json.readTree(payload.content()).path("response").path("docs");
            List<ExternalReference> references = new ArrayList<>();
            for (JsonNode doc : docs) {
                if (references.size() >= maxMetadataItems) break;
                String identifier = text(doc, "identifier");
                if (identifier.isBlank()) continue;
                VideoFile file = selectPlayableFile(base, identifier);
                if (file == null) continue;
                String externalId = identifier + "/" + file.name();
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("platform", "Internet Archive");
                metadata.put("identifier", identifier);
                metadata.put("fileName", file.name());
                metadata.put("mediaType", file.mediaType());
                metadata.put("duration", file.duration());
                metadata.put("fileSize", Long.toString(file.size()));
                metadata.put("downloads", Long.toString(doc.path("downloads").asLong(0)));
                metadata.put("publicdate", text(doc, "publicdate"));
                metadata.put("description", truncate(text(doc, "description"), 600));
                metadata.put("itemUrl", "https://archive.org/details/" + identifier);
                references.add(new ExternalReference(externalId, downloadUri(base, identifier, file.name()), firstText(text(doc, "title"), identifier), parseDate(firstText(text(doc, "publicdate"), text(doc, "date"))), metadata));
            }
            String next = references.isEmpty() ? cursor : references.getFirst().externalId();
            if (cursor != null) references = references.stream().takeWhile(ref -> !cursor.equals(ref.externalId())).toList();
            return new DiscoveryBatch(references, next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Internet Archive search payload", ex);
        }
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        RawPayload payload = http.fetch(reference);
        String declared = reference.metadata().getOrDefault("mediaType", payload.mediaType());
        return new RawPayload(reference, payload.finalUri(), payload.status(), declared, payload.content(), payload.etag(), payload.lastModified(), payload.redirectChain());
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            discover(source, null);
            return new HealthResult(true, (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private VideoFile selectPlayableFile(URI base, String identifier) {
        URI metadataUri = base.resolve("/metadata/" + segment(identifier));
        RawPayload metadata = http.fetch(new ExternalReference(identifier, metadataUri, identifier, null, Map.of()));
        try {
            JsonNode files = json.readTree(metadata.content()).path("files");
            VideoFile best = null;
            for (JsonNode file : files) {
                String name = text(file, "name");
                if (name.isBlank()) continue;
                long size = parseLong(text(file, "size"));
                if (size <= 0 || size > maxVideoBytes) continue;
                String mediaType = mediaTypeFor(name, text(file, "format"));
                if (mediaType.isBlank()) continue;
                VideoFile candidate = new VideoFile(name, size, mediaType, firstText(text(file, "length"), text(file, "duration")));
                if (best == null || score(candidate) > score(best)) best = candidate;
            }
            return best;
        } catch (Exception ex) {
            return null;
        }
    }

    private static URI origin(URI uri) {
        if (uri.getScheme() == null || uri.getAuthority() == null) throw new IllegalArgumentException("source endpoint must be absolute");
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private static URI downloadUri(URI base, String identifier, String fileName) {
        return base.resolve("/download/" + segment(identifier) + "/" + segment(fileName));
    }

    private static String mediaTypeFor(String name, String format) {
        String value = (name + " " + format).toLowerCase(Locale.ROOT);
        if (value.endsWith(".mp4") || value.contains("mpeg4") || value.contains("h.264")) return "video/mp4";
        if (value.endsWith(".webm") || value.contains("webm")) return "video/webm";
        if (value.endsWith(".ogv") || value.contains("ogg video")) return "video/ogg";
        return "";
    }

    private static int score(VideoFile file) {
        if ("video/mp4".equals(file.mediaType())) return 30 + (int) Math.min(20, file.size() / 1_000_000);
        if ("video/webm".equals(file.mediaType())) return 20;
        return 10;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (value.isArray()) return value.isEmpty() ? "" : value.get(0).asText("");
        return value.asText("");
    }

    private static long parseLong(String value) {
        try { return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim()); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        try { return Instant.parse(normalized); } catch (Exception ignored) {}
        try { return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); } catch (Exception ignored) {}
        if (normalized.matches("\\d{4}")) return LocalDate.of(Integer.parseInt(normalized), 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return null;
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max - 1) + "…";
    }

    private record VideoFile(String name, long size, String mediaType, String duration) {}
}
