package com.subtlesight.connectors;

import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface SourceConnector {
    SourceType type();
    DiscoveryBatch discover(Source source, String cursor);
    RawPayload fetch(ExternalReference reference);
    HealthResult healthCheck(Source source);

    record ExternalReference(String externalId, URI uri, String title, Instant publishedAt, Map<String,String> metadata) {}
    record DiscoveryBatch(List<ExternalReference> references, String nextCursor) {
        public DiscoveryBatch { references = references == null ? List.of() : List.copyOf(references); }
    }
    record RawPayload(ExternalReference reference, URI finalUri, int status, String mediaType, byte[] content,
                      String etag, String lastModified, List<URI> redirectChain) {}
    record HealthResult(boolean healthy, long latencyMillis, String errorClass, Instant checkedAt) {}
}

