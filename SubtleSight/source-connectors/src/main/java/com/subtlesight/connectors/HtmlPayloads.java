package com.subtlesight.connectors;

import com.subtlesight.connectors.SourceConnector.ExternalReference;
import com.subtlesight.connectors.SourceConnector.RawPayload;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class HtmlPayloads {
    private HtmlPayloads() {}

    static RawPayload fromMetadata(ExternalReference reference, URI finalUri, String sourceLabel) {
        Map<String,String> m = reference.metadata() == null ? Map.of() : reference.metadata();
        String title = first(reference.title(), m.get("title"), finalUri.toString());
        String summary = first(m.get("summary"), m.get("description"), m.get("text"), "");
        String author = first(m.get("author"), m.get("owner"), m.get("by"), sourceLabel);
        String extra = m.entrySet().stream()
                .filter(e -> !List.of("title","summary","description","text","author","owner","by","html").contains(e.getKey()))
                .map(e -> "<li><strong>" + escape(e.getKey()) + ":</strong> " + escape(e.getValue()) + "</li>")
                .reduce("", String::concat);
        String published = reference.publishedAt() == null ? "" : "<time datetime=\"" + escape(reference.publishedAt().toString()) + "\">" + escape(reference.publishedAt().toString()) + "</time>";
        String html = first(m.get("html"), """
                <!doctype html><html><head><meta charset="UTF-8"><title>%s</title>
                <meta name="author" content="%s"><meta property="article:published_time" content="%s">
                <link rel="canonical" href="%s"></head><body><article>
                <h1>%s</h1><p>%s</p><p>Source: %s</p>%s<ul>%s</ul>
                <p><a href="%s">Original source</a></p>
                </article></body></html>
                """.formatted(escape(title), escape(author), escape(reference.publishedAt() == null ? "" : reference.publishedAt().toString()),
                escape(finalUri.toString()), escape(title), escape(summary), escape(sourceLabel), published, extra, escape(finalUri.toString())));
        return new RawPayload(reference, finalUri, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), null, null, List.of());
    }

    static String escape(String value) {
        if (value == null) return "";
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }

    static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    static Instant epochSeconds(long seconds) {
        return seconds <= 0 ? null : Instant.ofEpochSecond(seconds);
    }
}
