package com.subtlesight.connectors;

import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;
import org.jsoup.Jsoup;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WebSourceConnector implements SourceConnector {
    private final SafeHttpClient http;
    private final SourceType type;
    public WebSourceConnector(SafeHttpClient http, SourceType type) { this.http=http; this.type=type; }
    @Override public SourceType type() { return type; }
    @Override public DiscoveryBatch discover(Source source, String cursor) {
        var ref = new ExternalReference(source.endpoint(), URI.create(source.endpoint()), source.name(), null, Map.of());
        var page = http.fetch(ref);
        if (page.status() < 200 || page.status() >= 300) throw new IllegalStateException("source returned HTTP " + page.status());
        if (type == SourceType.WEBSITE || type == SourceType.SITEMAP) {
            var doc = Jsoup.parse(new String(page.content(), java.nio.charset.StandardCharsets.UTF_8), page.finalUri().toString());
            List<ExternalReference> refs = doc.select("a[href]").stream().limit(100)
                    .map(a -> new ExternalReference(Hashing.sha256(a.absUrl("href")), URI.create(a.absUrl("href")), a.text(), null, Map.of()))
                    .filter(r -> r.uri().isAbsolute()).toList();
            return new DiscoveryBatch(refs, Hashing.sha256(page.content()));
        }
        return new DiscoveryBatch(List.of(ref), Hashing.sha256(page.content()));
    }
    @Override public RawPayload fetch(ExternalReference reference) { return http.fetch(reference); }
    @Override public HealthResult healthCheck(Source source) {
        long start=System.nanoTime(); try { RawPayload response = http.fetch(new ExternalReference(source.endpoint(), URI.create(source.endpoint()), source.name(), null, Map.of())); boolean healthy = response.status() >= 200 && response.status() < 400; return new HealthResult(healthy,(System.nanoTime()-start)/1_000_000,healthy?null:"HTTP_"+response.status(),Instant.now()); }
        catch(Exception ex){ return new HealthResult(false,(System.nanoTime()-start)/1_000_000,ex.getClass().getSimpleName(),Instant.now()); }
    }
}
