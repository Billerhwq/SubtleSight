package com.subtlesight.calendar.connectors;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record CalendarFetch(URI requestedUri, URI finalUri, int status, String mediaType, byte[] content,
                            String etag, String lastModified, Map<String, List<String>> headers) {
    public CalendarFetch {
        mediaType = mediaType == null ? "application/octet-stream" : mediaType;
        content = content == null ? new byte[0] : content;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
