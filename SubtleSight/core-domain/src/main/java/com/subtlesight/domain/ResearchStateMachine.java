package com.subtlesight.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static com.subtlesight.domain.Models.ResearchStatus;

public final class ResearchStateMachine {
    private static final Map<ResearchStatus, EnumSet<ResearchStatus>> ALLOWED = new EnumMap<>(ResearchStatus.class);
    static {
        allow(ResearchStatus.CREATED, ResearchStatus.SCOPING, ResearchStatus.CANCELLED);
        allow(ResearchStatus.SCOPING, ResearchStatus.PLANNING, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.PLANNING, ResearchStatus.SEARCHING, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.SEARCHING, ResearchStatus.READING, ResearchStatus.PAUSED, ResearchStatus.PARTIAL, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.READING, ResearchStatus.EXTRACTING, ResearchStatus.PAUSED, ResearchStatus.PARTIAL, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.EXTRACTING, ResearchStatus.REFLECTING, ResearchStatus.PARTIAL, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.REFLECTING, ResearchStatus.SEARCHING, ResearchStatus.WRITING, ResearchStatus.PARTIAL, ResearchStatus.CANCELLED);
        allow(ResearchStatus.WRITING, ResearchStatus.VERIFYING, ResearchStatus.PARTIAL, ResearchStatus.FAILED, ResearchStatus.CANCELLED);
        allow(ResearchStatus.VERIFYING, ResearchStatus.SEARCHING, ResearchStatus.COMPLETED, ResearchStatus.PARTIAL, ResearchStatus.FAILED);
        allow(ResearchStatus.PAUSED, ResearchStatus.SEARCHING, ResearchStatus.CANCELLED);
        for (ResearchStatus terminal : EnumSet.of(ResearchStatus.COMPLETED, ResearchStatus.PARTIAL, ResearchStatus.CANCELLED, ResearchStatus.FAILED))
            ALLOWED.put(terminal, EnumSet.noneOf(ResearchStatus.class));
    }
    private ResearchStateMachine() {}
    private static void allow(ResearchStatus from, ResearchStatus... to) { ALLOWED.put(from, EnumSet.copyOf(java.util.List.of(to))); }
    public static void requireTransition(ResearchStatus from, ResearchStatus to) {
        if (from == null || to == null || !ALLOWED.getOrDefault(from, EnumSet.noneOf(ResearchStatus.class)).contains(to))
            throw new IllegalStateException("illegal research transition: " + from + " -> " + to);
    }
}

