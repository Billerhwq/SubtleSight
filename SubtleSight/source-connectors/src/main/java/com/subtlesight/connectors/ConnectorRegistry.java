package com.subtlesight.connectors;

import com.subtlesight.domain.Models.SourceType;

import java.util.EnumMap;
import java.util.Map;

public final class ConnectorRegistry {
    private final Map<SourceType,SourceConnector> connectors = new EnumMap<>(SourceType.class);
    public ConnectorRegistry register(SourceConnector connector) { connectors.put(connector.type(), connector); return this; }
    public SourceConnector require(SourceType type) {
        SourceConnector connector = connectors.get(type);
        if (connector == null) throw new IllegalArgumentException("connector not configured: " + type);
        return connector;
    }
}

