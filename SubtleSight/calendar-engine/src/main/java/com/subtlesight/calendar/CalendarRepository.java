package com.subtlesight.calendar;

import com.subtlesight.calendar.CalendarModels.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository {
    CalendarSource saveSource(CalendarSource source);
    Optional<CalendarSource> findSource(UUID id);
    Optional<CalendarSource> findSourceByKey(String key);
    List<CalendarSource> listSources();
    RawCalendarSnapshot saveSnapshot(RawCalendarSnapshot snapshot);
    Optional<CalendarEvent> findEvent(UUID id);
    Optional<CalendarEvent> findEventByKey(String eventKey);
    CalendarEvent saveEvent(CalendarEvent event);
    CalendarEventEvidence saveEvidence(CalendarEventEvidence evidence);
    CalendarEventRevision saveRevision(CalendarEventRevision revision);
    List<CalendarEvent> listEvents(CalendarQuery query);
    List<CalendarEventEvidence> listEvidence(UUID eventId);
    List<CalendarEventRevision> listRevisions(UUID eventId);
    void updateSourceStatus(UUID sourceId, CalendarSourceHealth health, Instant attemptedAt, Instant successAt,
                            Instant nextAllowedAt, String warning, int httpStatus, int parsed, int inserted, int updated);
    long countEvents();
}
