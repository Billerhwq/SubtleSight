package com.subtlesight.calendar.connectors;

import com.subtlesight.connectors.SafeHttpClient;
import com.subtlesight.connectors.SourceConnector.ExternalReference;

import java.net.URI;
import java.util.Map;

public final class SafeCalendarFetcher implements CalendarFetcher {
    private final SafeHttpClient http;

    public SafeCalendarFetcher(SafeHttpClient http) {
        this.http = http;
    }

    @Override public CalendarFetch fetch(URI uri) {
        var payload = http.fetch(new ExternalReference(uri.toString(), uri, "calendar", null, Map.of()));
        return new CalendarFetch(uri, payload.finalUri(), payload.status(), payload.mediaType(), payload.content(),
                payload.etag(), payload.lastModified(), Map.of());
    }
}
