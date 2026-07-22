package com.subtlesight.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.calendar.CalendarModels.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FinancialCalendarEngine {
    private final CalendarRepository repository;
    private final ObjectMapper json;

    public FinancialCalendarEngine(CalendarRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.json = mapper.copy().findAndRegisterModules();
    }

    public CalendarMergeResult merge(CalendarSource source, RawCalendarSnapshot snapshot, List<CalendarEventCandidate> candidates, Instant now) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<CalendarEvent> events = new ArrayList<>();
        for (CalendarEventCandidate candidate : candidates) {
            String key = CalendarEventKeyFactory.key(candidate);
            CalendarEvent event = repository.findEventByKey(key).orElse(null);
            boolean isNew = event == null;
            Map<String, Object> changes = new LinkedHashMap<>();
            if (isNew) {
                event = new CalendarEvent(UUID.randomUUID(), key, candidate.indicatorCode(), candidate.nameZh(), candidate.nameOriginal(),
                        candidate.countryCode(), candidate.region(), candidate.currency(), candidate.category(), candidate.importance(),
                        source.name(), candidate.scheduledAtUtc(), candidate.sourceTimezone(), candidate.scheduledLocalText(),
                        candidate.period(), candidate.actual(), candidate.forecast(), candidate.previous(), candidate.revisedPrevious(),
                        candidate.unit(), statusWithActual(candidate.status(), candidate.actual()), officialUrl(candidate, source),
                        source.id(), now, now, candidate.actual() == null ? null : now, 1);
                inserted++;
            } else {
                event = mergeIntoExisting(event, candidate, source, now, changes);
                if (changes.isEmpty()) unchanged++;
                else updated++;
            }
            CalendarEvent saved = repository.saveEvent(event);
            repository.saveEvidence(new CalendarEventEvidence(UUID.randomUUID(), saved.id(), source.id(), snapshot.id(),
                    candidate.sourceEventId(), candidate.sourceUrl().isBlank() ? source.endpoint() : candidate.sourceUrl(),
                    snapshot.fetchedAt(), snapshot.parserVersion(), write(candidate.rawFields()), write(candidate.warnings()), source.tier()));
            if (!isNew && !changes.isEmpty()) {
                repository.saveRevision(new CalendarEventRevision(UUID.randomUUID(), saved.id(), now, source.id(), write(changes), "calendar-merge"));
            }
            events.add(saved);
        }
        return new CalendarMergeResult(inserted, updated, unchanged, events);
    }

    private CalendarEvent mergeIntoExisting(CalendarEvent old, CalendarEventCandidate candidate, CalendarSource source, Instant now, Map<String, Object> changes) {
        boolean official = source.tier() == CalendarSourceTier.OFFICIAL;
        String actual = chooseValue("actual", old.actual(), candidate.actual(), official || old.actual() == null, changes);
        String forecast = chooseValue("forecast", old.forecast(), candidate.forecast(), old.forecast() == null || source.tier() == CalendarSourceTier.AGGREGATOR, changes);
        String previous = chooseValue("previous", old.previous(), candidate.previous(), official || old.previous() == null, changes);
        String revised = chooseValue("revisedPrevious", old.revisedPrevious(), candidate.revisedPrevious(), official || old.revisedPrevious() == null, changes);
        String unit = chooseValue("unit", old.unit(), candidate.unit(), old.unit().isBlank() || official, changes);
        String officialUrl = chooseValue("officialUrl", old.officialUrl(), officialUrl(candidate, source), old.officialUrl().isBlank() || official, changes);
        String localText = chooseValue("scheduledLocalText", old.scheduledLocalText(), candidate.scheduledLocalText(), old.scheduledLocalText().isBlank() || official, changes);
        Instant scheduled = chooseInstant("scheduledAtUtc", old.scheduledAtUtc(), candidate.scheduledAtUtc(), old.scheduledAtUtc() == null || official, changes);
        CalendarImportance importance = chooseImportance(old.importance(), candidate.importance(), source, changes);
        CalendarEventStatus status = chooseStatus(old.status(), statusWithActual(candidate.status(), actual), changes);
        Instant releasedAt = old.releasedAt();
        if (releasedAt == null && status == CalendarEventStatus.RELEASED) releasedAt = now;
        int version = changes.isEmpty() ? old.version() : old.version() + 1;
        return new CalendarEvent(old.id(), old.eventKey(), old.indicatorCode(), old.nameZh(), old.nameOriginal(),
                old.countryCode(), old.region(), old.currency(), old.category(), importance,
                importance != old.importance() ? source.name() : old.importanceSource(), scheduled, old.sourceTimezone(), localText,
                blankOr(candidate.period(), old.period()), actual, forecast, previous, revised, unit, status,
                officialUrl, old.primarySourceId() == null || official ? source.id() : old.primarySourceId(),
                old.firstSeenAt(), now, releasedAt, version);
    }

    private static CalendarEventStatus statusWithActual(CalendarEventStatus status, String actual) {
        if (actual != null && !actual.isBlank()) return CalendarEventStatus.RELEASED;
        return status == null ? CalendarEventStatus.SCHEDULED : status;
    }

    private static CalendarEventStatus chooseStatus(CalendarEventStatus old, CalendarEventStatus candidate, Map<String, Object> changes) {
        CalendarEventStatus next = old;
        if (candidate == CalendarEventStatus.CANCELLED || candidate == CalendarEventStatus.DELAYED || candidate == CalendarEventStatus.REVISED || candidate == CalendarEventStatus.RELEASED) next = candidate;
        if (next != old) changes.put("status", Map.of("old", old.name(), "new", next.name()));
        return next;
    }

    private static CalendarImportance chooseImportance(CalendarImportance old, CalendarImportance candidate, CalendarSource source, Map<String, Object> changes) {
        CalendarImportance next = old;
        if (candidate.ordinal() > old.ordinal() || source.tier() == CalendarSourceTier.AGGREGATOR && old == CalendarImportance.MEDIUM) next = candidate;
        if (next != old) changes.put("importance", Map.of("old", old.name(), "new", next.name()));
        return next;
    }

    private static String chooseValue(String field, String old, String candidate, boolean allowed, Map<String, Object> changes) {
        if (!allowed || candidate == null || candidate.isBlank() || candidate.equals(old)) return old;
        changes.put(field, Map.of("old", old == null ? "" : old, "new", candidate));
        return candidate;
    }

    private static Instant chooseInstant(String field, Instant old, Instant candidate, boolean allowed, Map<String, Object> changes) {
        if (!allowed || candidate == null || candidate.equals(old)) return old;
        changes.put(field, Map.of("old", old == null ? "" : old.toString(), "new", candidate.toString()));
        return candidate;
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String officialUrl(CalendarEventCandidate candidate, CalendarSource source) {
        if (!candidate.officialUrl().isBlank()) return candidate.officialUrl();
        return source.tier() == CalendarSourceTier.OFFICIAL ? candidate.sourceUrl() : "";
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("calendar json serialization failed", ex);
        }
    }
}
