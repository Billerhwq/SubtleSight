package com.subtlesight.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.calendar.CalendarModels.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEngineTest {
    @Test void eventKeyUsesCountryIndicatorPeriodAndLocalDate() {
        CalendarEventCandidate a = candidate("US", "US_CPI", "2026-06", Instant.parse("2026-07-17T12:30:00Z"), null);
        CalendarEventCandidate b = candidate("US", "US_CPI", "2026-06", Instant.parse("2026-07-17T16:00:00Z"), null);
        assertThat(CalendarEventKeyFactory.key(a)).isEqualTo(CalendarEventKeyFactory.key(b));
    }

    @Test void aggregatorAddsForecastWithoutOverwritingOfficialActual() {
        MemoryRepo repo = new MemoryRepo();
        FinancialCalendarEngine engine = new FinancialCalendarEngine(repo, new ObjectMapper());
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        CalendarSource official = source(CalendarSourceTier.OFFICIAL);
        CalendarSource agg = source(CalendarSourceTier.AGGREGATOR);
        RawCalendarSnapshot snap = snapshot(official, now);
        engine.merge(official, snap, List.of(candidate("US", "US_CPI", "2026-06", Instant.parse("2026-07-17T12:30:00Z"), "3.0%")), now);
        CalendarMergeResult result = engine.merge(agg, snapshot(agg, now), List.of(new CalendarEventCandidate(
                "te-1", "https://example.test", "US", "", "USD", "CPI", "CPI", "US_CPI", CalendarCategory.INFLATION,
                CalendarImportance.HIGH, Instant.parse("2026-07-17T12:30:00Z"), "America/New_York", "8:30 AM",
                "2026-06", "2.9%", "3.1%", "2.8%", null, "%", CalendarEventStatus.RELEASED, "",
                Map.of("forecast", "3.1%"), List.of())), now.plusSeconds(60));
        assertThat(result.updated()).isEqualTo(1);
        CalendarEvent saved = repo.events.values().iterator().next();
        assertThat(saved.actual()).isEqualTo("3.0%");
        assertThat(saved.forecast()).isEqualTo("3.1%");
        assertThat(repo.revisions).hasSize(1);
        assertThat(repo.evidence).hasSize(2);
    }

    @Test void icsParserReadsBeaStyleEvents() {
        CalendarSource source = source(CalendarSourceTier.OFFICIAL);
        String ics = """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                SUMMARY:Gross Domestic Product\\, 4th Quarter and Year 2025 (Advance Estimate)
                DTSTART;VALUE=DATE-TIME:20260220T133000Z
                UID:bea-gdp
                DESCRIPTION:www.bea.gov
                END:VEVENT
                END:VCALENDAR
                """;
        ParsedCalendarBatch batch = new IcsCalendarParser(new IndicatorDictionary()).parse(ics, source);
        assertThat(batch.candidates()).singleElement().satisfies(c -> {
            assertThat(c.indicatorCode()).isEqualTo("US_GDP");
            assertThat(c.period()).isEqualTo("2025-Q4");
            assertThat(c.scheduledAtUtc()).isEqualTo(Instant.parse("2026-02-20T13:30:00Z"));
        });
    }

    private static CalendarEventCandidate candidate(String country, String code, String period, Instant at, String actual) {
        return new CalendarEventCandidate("id", "https://example.test", country, "", "USD", "CPI", "CPI", code,
                CalendarCategory.INFLATION, CalendarImportance.HIGH, at, "America/New_York", "8:30 AM", period, actual,
                null, null, null, "%", actual == null ? CalendarEventStatus.SCHEDULED : CalendarEventStatus.RELEASED,
                "https://official.test", Map.of(), List.of());
    }

    private static CalendarSource source(CalendarSourceTier tier) {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        return new CalendarSource(UUID.randomUUID(), tier == CalendarSourceTier.AGGREGATOR ? "agg" : "bea-ics",
                tier.name(), CalendarSourceType.OFFICIAL_ICS, tier, "https://example.test/calendar.ics",
                "manual", true, 0, 100, now, null, null, CalendarSourceHealth.HEALTHY, "test-v1", "", 0, 0, 0, 0,
                Set.of("US"), Set.of("GROWTH"), now, now);
    }

    private static RawCalendarSnapshot snapshot(CalendarSource source, Instant now) {
        return new RawCalendarSnapshot(UUID.randomUUID(), source.id(), source.key(), source.endpoint(), source.endpoint(), 200,
                "text/calendar", "", "", now, "hash-" + source.key(), 12, "test-v1", "body");
    }

    private static final class MemoryRepo implements CalendarRepository {
        final Map<String, CalendarEvent> events = new LinkedHashMap<>();
        final List<CalendarEventEvidence> evidence = new ArrayList<>();
        final List<CalendarEventRevision> revisions = new ArrayList<>();

        public CalendarSource saveSource(CalendarSource source) { return source; }
        public Optional<CalendarSource> findSource(UUID id) { return Optional.empty(); }
        public Optional<CalendarSource> findSourceByKey(String key) { return Optional.empty(); }
        public List<CalendarSource> listSources() { return List.of(); }
        public RawCalendarSnapshot saveSnapshot(RawCalendarSnapshot snapshot) { return snapshot; }
        public Optional<CalendarEvent> findEvent(UUID id) { return events.values().stream().filter(e -> e.id().equals(id)).findFirst(); }
        public Optional<CalendarEvent> findEventByKey(String key) { return Optional.ofNullable(events.get(key)); }
        public CalendarEvent saveEvent(CalendarEvent event) { events.put(event.eventKey(), event); return event; }
        public CalendarEventEvidence saveEvidence(CalendarEventEvidence evidence) { this.evidence.add(evidence); return evidence; }
        public CalendarEventRevision saveRevision(CalendarEventRevision revision) { revisions.add(revision); return revision; }
        public List<CalendarEvent> listEvents(CalendarQuery query) { return List.copyOf(events.values()); }
        public List<CalendarEventEvidence> listEvidence(UUID eventId) { return evidence; }
        public List<CalendarEventRevision> listRevisions(UUID eventId) { return revisions; }
        public void updateSourceStatus(UUID sourceId, CalendarSourceHealth health, Instant attemptedAt, Instant successAt, Instant nextAllowedAt, String warning, int httpStatus, int parsed, int inserted, int updated) {}
        public long countEvents() { return events.size(); }
    }
}
