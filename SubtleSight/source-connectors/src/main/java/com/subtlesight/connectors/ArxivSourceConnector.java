package com.subtlesight.connectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArxivSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final int maxItems;
    private final Duration minApiDelay;
    private long lastRequestNanos;

    public ArxivSourceConnector(SafeHttpClient http) {
        this(http, 20, Duration.ofSeconds(3));
    }

    ArxivSourceConnector(SafeHttpClient http, int maxItems) {
        this(http, maxItems, Duration.ZERO);
    }

    ArxivSourceConnector(SafeHttpClient http, int maxItems, Duration minApiDelay) {
        this.http = http;
        this.maxItems = Math.max(1, maxItems);
        this.minApiDelay = minApiDelay == null ? Duration.ZERO : minApiDelay;
    }

    @Override public SourceType type() { return SourceType.ARXIV; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        waitForArxiv();
        RawPayload feed = http.fetch(new ExternalReference(source.id().toString(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(feed.content()))) {
            List<ExternalReference> refs = new SyndFeedInput().build(reader).getEntries().stream().map(this::map).toList();
            String next = refs.isEmpty() ? cursor : refs.getFirst().externalId();
            if (cursor != null) refs = refs.stream().takeWhile(ref -> !cursor.equals(ref.externalId())).toList();
            return new DiscoveryBatch(refs.stream().limit(maxItems).toList(), next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid arXiv Atom payload", ex);
        }
    }

    private ExternalReference map(SyndEntry entry) {
        Instant published = entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant();
        String id = HtmlPayloads.first(entry.getUri(), entry.getLink(), entry.getTitle());
        URI uri = URI.create(HtmlPayloads.first(entry.getLink(), entry.getUri()));
        Map<String,String> metadata = new LinkedHashMap<>();
        metadata.put("title", entry.getTitle());
        metadata.put("summary", entry.getDescription() == null ? "" : entry.getDescription().getValue());
        metadata.put("author", entry.getAuthor() == null ? "" : entry.getAuthor());
        metadata.put("categories", entry.getCategories() == null ? "" : entry.getCategories().stream().map(c -> c.getName()).reduce((a,b) -> a + ", " + b).orElse(""));
        return new ExternalReference(id, uri, entry.getTitle(), published, metadata);
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        return HtmlPayloads.fromMetadata(reference, reference.uri(), "arXiv");
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            return new HealthResult(!discover(source, null).references().isEmpty(), (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private synchronized void waitForArxiv() {
        if (minApiDelay.isZero() || minApiDelay.isNegative()) return;
        long now = System.nanoTime();
        if (lastRequestNanos > 0) {
            long remaining = minApiDelay.toNanos() - (now - lastRequestNanos);
            if (remaining > 0) {
                try { Thread.sleep(Math.max(1, Duration.ofNanos(remaining).toMillis())); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("arXiv rate sleep interrupted", ex); }
                now = System.nanoTime();
            }
        }
        lastRequestNanos = now;
    }
}
