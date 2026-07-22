package com.subtlesight.calendar.connectors;

import com.subtlesight.calendar.CalendarModels.CalendarSource;

import java.util.ArrayList;
import java.util.List;

public final class CalendarConnectorRegistry {
    private final List<CalendarSourceConnector> connectors = new ArrayList<>();

    public CalendarConnectorRegistry register(CalendarSourceConnector connector) {
        connectors.add(connector);
        return this;
    }

    public CalendarSourceConnector connectorFor(CalendarSource source) {
        return connectors.stream().filter(c -> c.supports(source)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no calendar connector for " + source.key()));
    }
}
