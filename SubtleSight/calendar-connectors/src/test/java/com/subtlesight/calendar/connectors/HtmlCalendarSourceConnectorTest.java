package com.subtlesight.calendar.connectors;

import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.IndicatorDictionary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCalendarSourceConnectorTest {
    private final HtmlCalendarSourceConnector connector = new HtmlCalendarSourceConnector(uri -> fetch(uri, ""), new IndicatorDictionary());

    @Test void parsesOnsReleaseCalendarAttributes() {
        CalendarSource source = source("ons-release-calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL, "https://www.ons.gov.uk/releasecalendar");
        CalendarFetch fetch = fetch(URI.create(source.endpoint()), """
                <html><body><li><a href="/releases/cpi2026" data-gtm-release-title="Consumer price inflation, UK: June 2026" data-gtm-release-url="/releases/cpi2026" data-gtm-release-date="20260717" data-gtm-release-time="09:30">CPI</a><div><span>Published</span></div></li></body></html>
                """);
        ParsedCalendarBatch batch = connector.parse(source, fetch);
        assertThat(batch.candidates()).singleElement().satisfies(c -> {
            assertThat(c.countryCode()).isEqualTo("GB");
            assertThat(c.indicatorCode()).isEqualTo("GB_CPI");
            assertThat(c.status()).isEqualTo(CalendarEventStatus.RELEASED);
            assertThat(c.scheduledAtUtc()).isEqualTo(Instant.parse("2026-07-17T08:30:00Z"));
        });
    }

    @Test void parsesTradingEconomicsActualForecastPreviousWithoutNeedingPaidApi() {
        CalendarSource source = source("tradingeconomics-calendar", CalendarSourceType.AGGREGATOR_HTML, CalendarSourceTier.AGGREGATOR, "https://tradingeconomics.com/calendar");
        CalendarFetch fetch = fetch(URI.create(source.endpoint()), """
                <table><tr data-url="/united-states/inflation-cpi" data-id="42" data-country="united states" data-category="inflation rate" data-event="inflation rate" data-symbol='CPI'>
                <td class=' 2026-07-17'><span class="event-0 calendar-date-1">12:30 PM</span></td>
                <td><table><tr><td class="calendar-iso">US</td></tr></table></td>
                <td><a class='calendar-event' href='/united-states/inflation-cpi'>Inflation Rate</a> <span class="calendar-reference">JUN</span></td>
                <td><a><span id='actual'>3.0%</span></a></td><td><span id='previous'>2.8%</span></td><td><a id='consensus'>3.1%</a></td><td><a id='forecast'>3.1%</a></td></tr></table>
                """);
        ParsedCalendarBatch batch = connector.parse(source, fetch);
        assertThat(batch.candidates()).singleElement().satisfies(c -> {
            assertThat(c.countryCode()).isEqualTo("US");
            assertThat(c.actual()).isEqualTo("3.0%");
            assertThat(c.forecast()).isEqualTo("3.1%");
            assertThat(c.previous()).isEqualTo("2.8%");
        });
    }

    @Test void emptyParserResultIsExplicitDriftWarning() {
        CalendarSource source = source("ons-release-calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL, "https://www.ons.gov.uk/releasecalendar");
        ParsedCalendarBatch batch = connector.parse(source, fetch(URI.create(source.endpoint()), "<html><body>changed</body></html>"));
        assertThat(batch.candidates()).isEmpty();
        assertThat(batch.warnings()).contains("PARSER_DRIFT_EMPTY_HTML");
    }

    private static CalendarFetch fetch(URI uri, String body) {
        return new CalendarFetch(uri, uri, 200, "text/html", body.getBytes(StandardCharsets.UTF_8), "", "", Map.of());
    }

    private static CalendarSource source(String key, CalendarSourceType type, CalendarSourceTier tier, String endpoint) {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        return new CalendarSource(UUID.randomUUID(), key, key, type, tier, endpoint, "manual", true, 0, 100, now, null, null,
                CalendarSourceHealth.HEALTHY, "test-v1", "", 0, 0, 0, 0, Set.of(), Set.of(), now, now);
    }
}
