package com.subtlesight.connectors;

import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RssSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    public RssSourceConnector(SafeHttpClient http) { this.http = http; }
    @Override public SourceType type() { return SourceType.RSS; }
    @Override public DiscoveryBatch discover(Source source, String cursor) {
        RawPayload feed = http.fetch(new ExternalReference(source.id().toString(), URI.create(source.endpoint()), source.name(), null, Map.of()));
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(feed.content()))) {
            List<ExternalReference> refs = new SyndFeedInput().build(reader).getEntries().stream().map(this::map).toList();
            String next = refs.isEmpty() ? cursor : refs.getFirst().externalId();
            if (cursor != null) refs = refs.stream().takeWhile(ref -> !cursor.equals(ref.externalId())).toList();
            return new DiscoveryBatch(refs, next);
        } catch (Exception ex) { throw new IllegalArgumentException("invalid RSS/Atom feed", ex); }
    }
    private ExternalReference map(SyndEntry e) {
        Instant published = e.getPublishedDate() == null ? null : e.getPublishedDate().toInstant();
        String id = e.getUri() != null ? e.getUri() : e.getLink();
        return new ExternalReference(id, URI.create(e.getLink()), e.getTitle(), published, Map.of("author", e.getAuthor() == null ? "" : e.getAuthor()));
    }
    @Override public RawPayload fetch(ExternalReference reference) { return http.fetch(reference); }
    @Override public HealthResult healthCheck(Source source) {
        long start = System.nanoTime(); try { discover(source, null); return new HealthResult(true, (System.nanoTime()-start)/1_000_000, null, Instant.now()); }
        catch (Exception ex) { return new HealthResult(false, (System.nanoTime()-start)/1_000_000, ex.getClass().getSimpleName(), Instant.now()); }
    }
}

