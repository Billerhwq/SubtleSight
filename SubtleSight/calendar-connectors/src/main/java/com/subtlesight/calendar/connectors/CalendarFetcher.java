package com.subtlesight.calendar.connectors;

import java.net.URI;

public interface CalendarFetcher {
    CalendarFetch fetch(URI uri);
}
