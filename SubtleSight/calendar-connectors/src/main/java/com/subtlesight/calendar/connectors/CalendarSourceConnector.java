package com.subtlesight.calendar.connectors;

import com.subtlesight.calendar.CalendarModels.CalendarSource;
import com.subtlesight.calendar.CalendarModels.ParsedCalendarBatch;

import java.nio.charset.StandardCharsets;

public interface CalendarSourceConnector {
    boolean supports(CalendarSource source);
    CalendarFetch fetch(CalendarSource source);
    ParsedCalendarBatch parse(CalendarSource source, CalendarFetch fetch);

    default String text(CalendarFetch fetch) {
        return new String(fetch.content(), StandardCharsets.UTF_8);
    }
}
