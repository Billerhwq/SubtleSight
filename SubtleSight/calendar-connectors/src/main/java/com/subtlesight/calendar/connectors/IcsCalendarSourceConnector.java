package com.subtlesight.calendar.connectors;

import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.IcsCalendarParser;

import java.net.URI;

public final class IcsCalendarSourceConnector implements CalendarSourceConnector {
    private final CalendarFetcher fetcher;
    private final IcsCalendarParser parser;

    public IcsCalendarSourceConnector(CalendarFetcher fetcher, IcsCalendarParser parser) {
        this.fetcher = fetcher;
        this.parser = parser;
    }

    @Override public boolean supports(CalendarSource source) {
        return source.type() == CalendarSourceType.OFFICIAL_ICS;
    }

    @Override public CalendarFetch fetch(CalendarSource source) {
        return fetcher.fetch(URI.create(source.endpoint()));
    }

    @Override public ParsedCalendarBatch parse(CalendarSource source, CalendarFetch fetch) {
        if (fetch.status() < 200 || fetch.status() >= 300) {
            return new ParsedCalendarBatch(java.util.List.of(), java.util.List.of("HTTP_" + fetch.status()), source.parserVersion());
        }
        return parser.parse(text(fetch), source);
    }
}
