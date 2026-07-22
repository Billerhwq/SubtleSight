package com.subtlesight.connectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProductHuntSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final int maxItems;

    public ProductHuntSourceConnector(SafeHttpClient http) {
        this(http, 15);
    }

    ProductHuntSourceConnector(SafeHttpClient http, int maxItems) {
        this.http = http;
        this.maxItems = Math.max(1, maxItems);
    }

    @Override public SourceType type() { return SourceType.PRODUCT_HUNT; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        RawPayload feed = http.fetch(new ExternalReference(source.id().toString(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(feed.content()))) {
            List<ExternalReference> refs = new SyndFeedInput().build(reader).getEntries().stream().map(this::map).toList();
            String next = refs.isEmpty() ? cursor : refs.getFirst().externalId();
            if (cursor != null) refs = refs.stream().takeWhile(ref -> !cursor.equals(ref.externalId())).toList();
            return new DiscoveryBatch(refs.stream().limit(maxItems).toList(), next);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Product Hunt feed payload", ex);
        }
    }

    private ExternalReference map(SyndEntry entry) {
        Instant published = entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant();
        String id = HtmlPayloads.first(entry.getUri(), entry.getLink(), entry.getTitle());
        Map<String,String> metadata = new LinkedHashMap<>();
        metadata.put("title", entry.getTitle());
        metadata.put("summary", entry.getDescription() == null ? "" : entry.getDescription().getValue());
        metadata.put("author", entry.getAuthor() == null ? "" : entry.getAuthor());
        metadata.put("platform", "Product Hunt");
        return new ExternalReference(id, URI.create(entry.getLink()), entry.getTitle(), published, metadata);
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        return HtmlPayloads.fromMetadata(reference, reference.uri(), "Product Hunt");
    }

    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime();
        try {
            return new HealthResult(!discover(source, null).references().isEmpty(), (System.nanoTime() - start) / 1_000_000, null, Instant.now());
        } catch (Exception ex) {
            return new HealthResult(false, (System.nanoTime() - start) / 1_000_000, ex.getClass().getSimpleName(), Instant.now());
        }
    }
}
