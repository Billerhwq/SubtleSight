package com.subtlesight.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optional OpenCLI bridge for browser-backed video hot lists and downloads. */
public final class OpenCliVideoSourceConnector implements SourceConnector {
    private final ObjectMapper json;
    private final String command;
    private final Path downloadRoot;
    private final Duration timeout;

    public OpenCliVideoSourceConnector(ObjectMapper json, String command, Path downloadRoot) {
        this(json, command, downloadRoot, Duration.ofMinutes(4));
    }

    public OpenCliVideoSourceConnector(ObjectMapper json, String command, Path downloadRoot, Duration timeout) {
        this.json = json;
        this.command = command == null || command.isBlank() ? "opencli" : command;
        this.downloadRoot = downloadRoot.toAbsolutePath().normalize();
        this.timeout = timeout == null ? Duration.ofMinutes(4) : timeout;
    }

    @Override public SourceType type() { return SourceType.VIDEO; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        Endpoint endpoint = Endpoint.parse(source.endpoint());
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add(endpoint.site());
        args.add(endpoint.action());
        args.add("--limit");
        args.add(Integer.toString(endpoint.limit()));
        args.add("-f");
        args.add("json");
        CommandResult result = run(args, timeout);
        if (result.exitCode() != 0) throw new IllegalStateException("opencli exited " + result.exitCode() + ": " + result.output());
        List<ExternalReference> references = mapRows(source, endpoint, result.output());
        String next = references.isEmpty() ? cursor : references.getFirst().externalId();
        if (cursor != null) references = references.stream().takeWhile(ref -> !cursor.equals(ref.externalId())).toList();
        return new DiscoveryBatch(references, next);
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        Map<String, String> metadata = reference.metadata() == null ? Map.of() : reference.metadata();
        boolean download = Boolean.parseBoolean(metadata.getOrDefault("download", "false"));
        if (!download) {
            byte[] bytes = videoMetadataHtml(reference).getBytes(StandardCharsets.UTF_8);
            return new RawPayload(reference, reference.uri(), 200, "text/html; charset=UTF-8", bytes, null, null, List.of());
        }
        String site = metadata.getOrDefault("site", "bilibili");
        String target = firstText(metadata.get("bvid"), metadata.get("id"), reference.uri().toString());
        Path outputDir = downloadRoot.resolve(site).resolve(safeName(reference.externalId()) + "-" + System.currentTimeMillis());
        try { Files.createDirectories(outputDir); } catch (IOException ex) { throw new IllegalStateException("cannot create opencli download dir", ex); }
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add(site);
        args.add("download");
        args.add(target);
        args.add("--output");
        args.add(outputDir.toString());
        CommandResult result = run(args, timeout);
        if (result.exitCode() != 0) throw new IllegalStateException("opencli download exited " + result.exitCode() + ": " + result.output());
        try {
            Path media = newestMedia(outputDir);
            byte[] bytes = Files.readAllBytes(media);
            String mediaType = mediaType(media);
            return new RawPayload(reference, media.toUri(), 200, mediaType, bytes, null, null, List.of());
        } catch (IOException ex) {
            throw new IllegalStateException("opencli downloaded media cannot be read", ex);
        }
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            CommandResult result = run(List.of(command, "--version"), Duration.ofSeconds(20));
            return new HealthResult(result.exitCode() == 0, (System.nanoTime() - start) / 1_000_000, result.exitCode() == 0 ? null : "Exit" + result.exitCode(), Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private List<ExternalReference> mapRows(Source source, Endpoint endpoint, String output) {
        try {
            JsonNode root = json.readTree(output);
            JsonNode rows = rows(root);
            List<ExternalReference> references = new ArrayList<>();
            for (JsonNode row : rows) {
                String bvid = firstText(text(row, "bvid"), text(row, "bv"));
                String id = firstText(bvid, text(row, "id"), text(row, "aid"), text(row, "url"));
                if (id.isBlank()) continue;
                String url = firstText(text(row, "url"), text(row, "link"), text(row, "href"), bvid.isBlank() ? "" : "https://www.bilibili.com/video/" + bvid);
                String title = firstText(text(row, "title"), text(row, "name"), id);
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("provider", "OpenCLI");
                metadata.put("platform", platform(endpoint.site()));
                metadata.put("site", endpoint.site());
                metadata.put("action", endpoint.action());
                metadata.put("download", Boolean.toString(endpoint.download()));
                metadata.put("bvid", bvid);
                metadata.put("id", id);
                metadata.put("author", firstText(text(row, "author"), text(row, "owner"), text(row, "up"), text(row, "uploader")));
                metadata.put("duration", firstText(text(row, "duration"), text(row, "length")));
                metadata.put("views", firstText(text(row, "views"), text(row, "play"), text(row, "view")));
                metadata.put("description", truncate(firstText(text(row, "desc"), text(row, "description"), text(row, "summary")), 500));
                references.add(new ExternalReference(id, URI.create(url), title, null, metadata));
            }
            return references;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid opencli json output", ex);
        }
    }

    private CommandResult run(List<String> args, Duration limit) {
        try {
            ProcessBuilder builder = new ProcessBuilder(platformArgs(args)).redirectErrorStream(true);
            Process process = builder.start();
            boolean done = process.waitFor(Math.max(1, limit.toMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                throw new IllegalStateException("opencli timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("opencli interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("opencli command failed to start", ex);
        }
    }

    private static List<String> platformArgs(List<String> args) {
        if (args.isEmpty()) return args;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String first = args.getFirst().toLowerCase(Locale.ROOT);
        if (os.contains("win") && (first.endsWith(".cmd") || first.endsWith(".bat"))) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add("cmd.exe");
            wrapped.add("/c");
            wrapped.addAll(args);
            return wrapped;
        }
        return args;
    }

    private static JsonNode rows(JsonNode root) {
        if (root.isArray()) return root;
        for (String field : List.of("items", "data", "list", "videos", "results", "rows")) {
            JsonNode value = root.path(field);
            if (value.isArray()) return value;
            for (String nested : List.of("items", "list", "videos", "results", "rows")) {
                JsonNode nestedValue = value.path(nested);
                if (nestedValue.isArray()) return nestedValue;
            }
        }
        throw new IllegalArgumentException("no rows array found");
    }

    private static String videoMetadataHtml(ExternalReference reference) {
        Map<String, String> metadata = reference.metadata() == null ? Map.of() : reference.metadata();
        String title = escape(firstText(reference.title(), reference.externalId()));
        String description = escape(metadata.getOrDefault("description", ""));
        return "<!doctype html><html><body><article><h1>" + title + "</h1><p>" + description + "</p><p>Provider: OpenCLI</p><p>Platform: " + escape(metadata.getOrDefault("platform", "")) + "</p><p>URL: " + escape(reference.uri().toString()) + "</p></article></body></html>";
    }

    private static Path newestMedia(Path outputDir) throws IOException {
        try (var stream = Files.walk(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> mediaType(path).startsWith("video/"))
                    .max(Comparator.comparing(path -> {
                        try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException ignored) { return 0L; }
                    }))
                    .orElseThrow(() -> new IllegalStateException("opencli did not create a playable video file"));
        }
    }

    private static String mediaType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".ogv") || name.endsWith(".ogg")) return "video/ogg";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        return name.endsWith(".mp4") || name.endsWith(".m4v") ? "video/mp4" : "application/octet-stream";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (value.isObject()) return firstText(text(value, "name"), text(value, "title"));
        return value.asText("");
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String platform(String site) {
        return switch (site.toLowerCase(Locale.ROOT)) {
            case "bilibili" -> "B站";
            case "youtube" -> "YouTube";
            case "xiaohongshu", "rednote" -> "小红书";
            case "twitter", "x" -> "Twitter/X";
            default -> site;
        };
    }

    private static String safeName(String value) {
        return value == null ? "video" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    record Endpoint(String site, String action, int limit, boolean download) {
        static Endpoint parse(String value) {
            URI uri = URI.create(value);
            if (!"opencli".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("not an opencli endpoint");
            String site = firstText(uri.getHost(), "bilibili");
            String action = uri.getPath() == null || uri.getPath().isBlank() ? "hot" : uri.getPath().replaceFirst("^/", "");
            Map<String, String> query = query(uri.getRawQuery());
            int limit = parseInt(query.get("limit"), 10);
            boolean download = Boolean.parseBoolean(query.getOrDefault("download", "false"));
            return new Endpoint(site, action, Math.min(Math.max(limit, 1), 50), download);
        }
        private static Map<String, String> query(String raw) {
            if (raw == null || raw.isBlank()) return Map.of();
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : raw.split("&")) {
                int index = part.indexOf('=');
                String key = index < 0 ? part : part.substring(0, index);
                String value = index < 0 ? "" : part.substring(index + 1);
                values.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return values;
        }
        private static int parseInt(String value, int fallback) {
            try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private record CommandResult(int exitCode, String output) {}
}
