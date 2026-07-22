package com.subtlesight.calendar.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteCalendarRepositoryTest {
    @TempDir Path temp;

    @Test void persistsSourceSnapshotEventEvidenceAndRevision() {
        SqliteCalendarRepository repo = new SqliteCalendarRepository(SqliteDataSourceFactory.create(temp.resolve("subtlesight.db")), new ObjectMapper());
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        CalendarSource source = repo.saveSource(new CalendarSource(UUID.randomUUID(), "fixture", "Fixture Calendar", CalendarSourceType.OFFICIAL_HTML,
                CalendarSourceTier.OFFICIAL, "https://example.test/calendar", "manual", true, 0, 100, now, null, null,
                CalendarSourceHealth.HEALTHY, "test-v1", "", 0, 0, 0, 0, Set.of("US"), Set.of("GROWTH"), now, now));
        RawCalendarSnapshot snapshot = repo.saveSnapshot(new RawCalendarSnapshot(UUID.randomUUID(), source.id(), source.key(), source.endpoint(),
                source.endpoint(), 200, "text/html", "", "", now, "hash", 4, "test-v1", "body"));
        CalendarEvent event = repo.saveEvent(new CalendarEvent(UUID.randomUUID(), "key", "US_GDP", "GDP", "GDP", "US", "", "USD",
                CalendarCategory.GROWTH, CalendarImportance.HIGH, source.name(), now, "America/New_York", "8:30 AM", "2026-Q2",
                null, "2.1%", "1.9%", null, "%", CalendarEventStatus.SCHEDULED, "https://example.test", source.id(), now, now, null, 1));
        repo.saveEvidence(new CalendarEventEvidence(UUID.randomUUID(), event.id(), source.id(), snapshot.id(), "source-event",
                source.endpoint(), now, "test-v1", "{\"forecast\":\"2.1%\"}", "[]", source.tier()));
        repo.saveRevision(new CalendarEventRevision(UUID.randomUUID(), event.id(), now.plusSeconds(10), source.id(), "{\"forecast\":{\"old\":\"\",\"new\":\"2.1%\"}}", "test"));
        assertThat(repo.listSources()).hasSize(1);
        assertThat(repo.listEvents(new CalendarQuery(now.minusSeconds(1), now.plusSeconds(1), Set.of("US"), Set.of(), Set.of(), Set.of(), 20))).hasSize(1);
        assertThat(repo.listEvidence(event.id())).hasSize(1);
        assertThat(repo.listRevisions(event.id())).hasSize(1);
        repo.updateSourceStatus(source.id(), CalendarSourceHealth.DEGRADED, now.plusSeconds(20), null, now.plusSeconds(1800), "PARSER_DRIFT", 200, 0, 0, 0);
        assertThat(repo.findSourceByKey("fixture")).get().satisfies(s -> {
            assertThat(s.health()).isEqualTo(CalendarSourceHealth.DEGRADED);
            assertThat(s.warning()).isEqualTo("PARSER_DRIFT");
        });
    }
}
