package com.subtlesight.provider.search;

import com.subtlesight.application.Ports.ReaderProvider;
import com.subtlesight.connectors.SafeHttpClient;
import com.subtlesight.connectors.SourceConnector.ExternalReference;

import java.net.URI;
import java.util.Map;

public final class SafeReaderProvider implements ReaderProvider {
    private final SafeHttpClient http;
    public SafeReaderProvider(SafeHttpClient http){this.http=http;}
    @Override public ReadResult read(URI uri){var result=http.fetch(new ExternalReference(uri.toString(),uri,uri.toString(),null,Map.of()));return new ReadResult(result.finalUri(),result.status(),result.mediaType(),result.content(),null,result.redirectChain());}
}

