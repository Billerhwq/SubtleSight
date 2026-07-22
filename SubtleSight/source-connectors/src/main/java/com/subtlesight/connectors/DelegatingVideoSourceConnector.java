package com.subtlesight.connectors;

import com.subtlesight.domain.Models.Source;
import com.subtlesight.domain.Models.SourceType;

import java.net.URI;

/** Routes VIDEO sources to a concrete strategy based on endpoint scheme. */
public final class DelegatingVideoSourceConnector implements SourceConnector {
    private final SourceConnector archive;
    private final SourceConnector openCli;

    public DelegatingVideoSourceConnector(SourceConnector archive, SourceConnector openCli) {
        this.archive = archive;
        this.openCli = openCli;
    }

    @Override public SourceType type() { return SourceType.VIDEO; }

    @Override public DiscoveryBatch discover(Source source, String cursor) {
        return delegate(source.endpoint()).discover(source, cursor);
    }

    @Override public RawPayload fetch(ExternalReference reference) {
        String provider = reference.metadata() == null ? "" : reference.metadata().getOrDefault("provider", "");
        if ("OpenCLI".equalsIgnoreCase(provider)) return openCli.fetch(reference);
        return archive.fetch(reference);
    }

    @Override public HealthResult healthCheck(Source source) {
        return delegate(source.endpoint()).healthCheck(source);
    }

    private SourceConnector delegate(String endpoint) {
        URI uri = URI.create(endpoint);
        return "opencli".equalsIgnoreCase(uri.getScheme()) ? openCli : archive;
    }
}
