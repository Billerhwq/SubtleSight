package com.subtlesight.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.calendar.*;
import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.connectors.*;
import com.subtlesight.calendar.storage.sqlite.SqliteCalendarRepository;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialCalendarServiceTest {
    @TempDir Path temp;

    @Test void refreshPersistsSnapshotMergesEventAndExportsIcs() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        SqliteCalendarRepository repo = new SqliteCalendarRepository(SqliteDataSourceFactory.create(temp.resolve("subtlesight.db")), json);
        CalendarConnectorRegistry registry = new CalendarConnectorRegistry().register(new FixtureConnector());
        FinancialCalendarEngine engine = new FinancialCalendarEngine(repo, json);
        FinancialCalendarService service = new FinancialCalendarService(repo, registry, engine, Clock.fixed(now, ZoneOffset.UTC));
        CalendarSource source = repo.saveSource(new CalendarSource(UUID.randomUUID(), "fixture-calendar", "Fixture Calendar",
                CalendarSourceType.CUSTOM_HTML, CalendarSourceTier.OFFICIAL, "https://fixture.test/calendar", "manual", true,
                3600, 100, now, null, null, CalendarSourceHealth.HEALTHY, "test-v1", "", 0, 0, 0, 0,
                Set.of("US"), Set.of("INFLATION"), now, now));
        CalendarRefreshReport report = service.refreshSource(source.id());
        assertThat(report.totalInserted()).isEqualTo(1);
        List<CalendarEvent> events = service.events(new CalendarQuery(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)), Set.of("US"), Set.of(), Set.of(), Set.of(), 20));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.actual()).isEqualTo("3.0%");
            assertThat(event.forecast()).isEqualTo("3.1%");
            assertThat(service.detail(event.id()).evidence()).hasSize(1);
        });
        assertThat(service.ics(new CalendarQuery(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)), Set.of("US"), Set.of(), Set.of(), Set.of(), 20)))
                .contains("BEGIN:VCALENDAR", "SUMMARY:US CPI");
        CalendarRefreshReport rateLimited = service.refreshSource(source.id());
        assertThat(rateLimited.results()).singleElement().satisfies(result -> assertThat(result.warning()).contains("RATE_LIMITED"));
    }

    private static final class FixtureConnector implements CalendarSourceConnector {
        @Override public boolean supports(CalendarSource source) { return source.key().equals("fixture-calendar"); }
        @Override public CalendarFetch fetch(CalendarSource source) {
            byte[] body = "fixture body".getBytes(StandardCharsets.UTF_8);
            return new CalendarFetch(URI.create(source.endpoint()), URI.create(source.endpoint()), 200, "text/html", body, "", "", Map.of());
        }
        @Override public ParsedCalendarBatch parse(CalendarSource source, CalendarFetch fetch) {
            return new ParsedCalendarBatch(List.of(new CalendarEventCandidate("fixture-cpi", source.endpoint(), "US", "", "USD",
                    "CPI", "CPI", "US_CPI", CalendarCategory.INFLATION, CalendarImportance.HIGH,
                    Instant.parse("2026-07-18T12:30:00Z"), "America/New_York", "8:30 AM", "2026-06",
                    "3.0%", "3.1%", "2.8%", null, "%", CalendarEventStatus.RELEASED, source.endpoint(),
                    Map.of("actual", "3.0%", "forecast", "3.1%"), List.of())), List.of(), "test-v1");
        }
    }
}
