package com.subtlesight.server;

import com.subtlesight.calendar.*;
import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.connectors.CalendarConnectorRegistry;
import com.subtlesight.calendar.connectors.CalendarFetch;
import com.subtlesight.calendar.connectors.CalendarSourceConnector;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class FinancialCalendarService {
    private final CalendarRepository repository;
    private final CalendarConnectorRegistry connectors;
    private final FinancialCalendarEngine engine;
    private final Clock clock;

    public FinancialCalendarService(CalendarRepository repository, CalendarConnectorRegistry connectors, FinancialCalendarEngine engine, Clock clock) {
        this.repository = repository;
        this.connectors = connectors;
        this.engine = engine;
        this.clock = clock;
    }

    public synchronized List<CalendarSource> bootstrapSources() {
        Instant now = clock.instant();
        List<CalendarSource> defaults = List.of(
                source("bea-ics", "BEA Release Calendar", CalendarSourceType.OFFICIAL_ICS, CalendarSourceTier.OFFICIAL,
                        "https://www.bea.gov/news/schedule/ics/online-calendar-subscription.ics", true, Duration.ofHours(6), Set.of("US"), Set.of("GROWTH", "CONSUMER", "TRADE"), now),
                source("bls-ics", "BLS News Release Calendar", CalendarSourceType.OFFICIAL_ICS, CalendarSourceTier.OFFICIAL,
                        "https://www.bls.gov/schedule/news_release/bls.ics", true, Duration.ofHours(6), Set.of("US"), Set.of("INFLATION", "EMPLOYMENT"), now),
                source("federalreserve-fomc", "Federal Reserve FOMC Calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL,
                        "https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm", true, Duration.ofHours(6), Set.of("US"), Set.of("CENTRAL_BANK"), now),
                source("ecb-meetings", "ECB Monetary Policy Calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL,
                        "https://www.ecb.europa.eu/press/calendars/mgcgc/html/index.en.html", true, Duration.ofHours(6), Set.of("EU"), Set.of("CENTRAL_BANK"), now),
                source("ons-release-calendar", "UK ONS Release Calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL,
                        "https://www.ons.gov.uk/releasecalendar", true, Duration.ofHours(6), Set.of("GB"), Set.of("GROWTH", "INFLATION", "EMPLOYMENT"), now),
                source("nbs-release-calendar", "China NBS Release Calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.OFFICIAL,
                        "https://www.stats.gov.cn/english/PressRelease/ReleaseCalendar/202512/t20251226_1962154.html", true, Duration.ofHours(6), Set.of("CN"), Set.of("GROWTH", "INFLATION", "INDUSTRY"), now),
                source("nyfed-calendar", "New York Fed Economic Indicators Calendar", CalendarSourceType.OFFICIAL_HTML, CalendarSourceTier.CROSS_CHECK,
                        "https://www.newyorkfed.org/research/calendars/nationalecon_cal", true, Duration.ofHours(6), Set.of("US"), Set.of("GROWTH", "INFLATION", "EMPLOYMENT"), now),
                source("tradingeconomics-calendar", "TradingEconomics Public Calendar", CalendarSourceType.AGGREGATOR_HTML, CalendarSourceTier.AGGREGATOR,
                        "https://tradingeconomics.com/calendar", true, Duration.ofMinutes(45), Set.of(), Set.of("INFLATION", "EMPLOYMENT", "GROWTH", "TRADE"), now),
                disabledSource("investing-calendar", "Investing.com Economic Calendar", CalendarSourceType.AGGREGATOR_HTML, CalendarSourceTier.AGGREGATOR,
                        "https://cn.investing.com/economic-calendar", "Disabled until current terms/access permit local collection; latest local probe returned forbidden.", now)
        );
        return defaults.stream().map(repository::saveSource).toList();
    }

    public synchronized CalendarRefreshReport refresh(String scope) {
        bootstrapSources();
        Instant started = clock.instant();
        List<CalendarSourceResult> results = new ArrayList<>();
        for (CalendarSource source : repository.listSources()) {
            if (!source.enabled()) continue;
            results.add(refreshSource(source, started));
        }
        Instant finished = clock.instant();
        return new CalendarRefreshReport(started, finished, results.size(), sum(results, CalendarSourceResult::parsed),
                sum(results, CalendarSourceResult::inserted), sum(results, CalendarSourceResult::updated),
                sum(results, CalendarSourceResult::unchanged), results);
    }

    public CalendarRefreshReport refreshSource(UUID sourceId) {
        bootstrapSources();
        CalendarSource source = repository.findSource(sourceId).orElseThrow();
        Instant started = clock.instant();
        CalendarSourceResult result = refreshSource(source, started);
        return new CalendarRefreshReport(started, clock.instant(), 1, result.parsed(), result.inserted(), result.updated(), result.unchanged(), List.of(result));
    }

    public List<CalendarEvent> events(CalendarQuery query) {
        bootstrapSources();
        return repository.listEvents(query);
    }

    public CalendarEventDetail detail(UUID id) {
        CalendarEvent event = repository.findEvent(id).orElseThrow();
        return new CalendarEventDetail(event, repository.listEvidence(id), repository.listRevisions(id));
    }

    public List<CalendarSource> sources() {
        bootstrapSources();
        return repository.listSources();
    }

    public String ics(CalendarQuery query) {
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SubtleSight//Financial Calendar//CN\r\n");
        for (CalendarEvent event : events(query)) {
            if (event.scheduledAtUtc() == null) continue;
            out.append("BEGIN:VEVENT\r\n")
                    .append("UID:").append(event.eventKey()).append("@subtlesight.local\r\n")
                    .append("DTSTAMP:").append(icsTime(Instant.now(clock))).append("\r\n")
                    .append("DTSTART:").append(icsTime(event.scheduledAtUtc())).append("\r\n")
                    .append("SUMMARY:").append(icsEscape(event.countryCode() + " " + event.nameOriginal())).append("\r\n")
                    .append("DESCRIPTION:").append(icsEscape("actual=" + nullSafe(event.actual()) + "; forecast=" + nullSafe(event.forecast()) + "; previous=" + nullSafe(event.previous()))).append("\r\n")
                    .append("URL:").append(icsEscape(event.officialUrl())).append("\r\n")
                    .append("END:VEVENT\r\n");
        }
        return out.append("END:VCALENDAR\r\n").toString();
    }

    private CalendarSourceResult refreshSource(CalendarSource source, Instant attemptedAt) {
        if (source.nextAllowedAt() != null && source.nextAllowedAt().isAfter(attemptedAt)) {
            String warning = "RATE_LIMITED_UNTIL_" + source.nextAllowedAt();
            return new CalendarSourceResult(source.id(), source.key(), source.name(), true, source.lastHttpStatus(), 0, 0, 0, 0,
                    warning, attemptedAt, null, source.nextAllowedAt(), "");
        }
        int httpStatus = 0;
        String contentHash = "";
        try {
            CalendarSourceConnector connector = connectors.connectorFor(source);
            CalendarFetch fetch = connector.fetch(source);
            httpStatus = fetch.status();
            String body = new String(fetch.content(), StandardCharsets.UTF_8);
            contentHash = CalendarEventKeyFactory.sha256(body);
            RawCalendarSnapshot snapshot = repository.saveSnapshot(new RawCalendarSnapshot(UUID.randomUUID(), source.id(), source.key(),
                    fetch.requestedUri().toString(), fetch.finalUri().toString(), fetch.status(), fetch.mediaType(), fetch.etag(), fetch.lastModified(),
                    attemptedAt, contentHash, fetch.content().length, source.parserVersion(), body));
            ParsedCalendarBatch parsed = connector.parse(source, fetch);
            CalendarMergeResult merge = engine.merge(source, snapshot, parsed.candidates(), attemptedAt);
            String warning = String.join(";", parsed.warnings());
            CalendarSourceHealth health = health(fetch.status(), parsed, warning);
            Instant success = health == CalendarSourceHealth.HEALTHY || health == CalendarSourceHealth.DEGRADED ? attemptedAt : null;
            Instant next = nextAttempt(source, attemptedAt, fetch.status(), health);
            repository.updateSourceStatus(source.id(), health, attemptedAt, success, next, warning, fetch.status(), parsed.candidates().size(), merge.inserted(), merge.updated());
            return new CalendarSourceResult(source.id(), source.key(), source.name(), success != null, fetch.status(), parsed.candidates().size(),
                    merge.inserted(), merge.updated(), merge.unchanged(), warning, attemptedAt, success, next, contentHash);
        } catch (Exception ex) {
            String warning = compactException(ex);
            Instant next = attemptedAt.plus(Duration.ofMinutes(30));
            repository.updateSourceStatus(source.id(), CalendarSourceHealth.DOWN, attemptedAt, null, next, warning, httpStatus, 0, 0, 0);
            return new CalendarSourceResult(source.id(), source.key(), source.name(), false, httpStatus, 0, 0, 0, 0, warning, attemptedAt, null, next, contentHash);
        }
    }

    private CalendarSourceHealth health(int status, ParsedCalendarBatch parsed, String warning) {
        if (status == 403 || status == 429) return CalendarSourceHealth.DOWN;
        if (status < 200 || status >= 300) return CalendarSourceHealth.DOWN;
        if (parsed.candidates().isEmpty() || warning.contains("PARSER_DRIFT")) return CalendarSourceHealth.DEGRADED;
        return CalendarSourceHealth.HEALTHY;
    }

    private Instant nextAttempt(CalendarSource source, Instant now, int status, CalendarSourceHealth health) {
        if (status == 403 || status == 429) return now.plus(Duration.ofHours(2));
        if (health == CalendarSourceHealth.DEGRADED) return now.plus(Duration.ofMinutes(30));
        return now.plusSeconds(Math.max(source.minIntervalSeconds(), 60));
    }

    private CalendarSource source(String key, String name, CalendarSourceType type, CalendarSourceTier tier, String endpoint,
                                  boolean enabled, Duration interval, Set<String> countries, Set<String> categories, Instant now) {
        return new CalendarSource(UUID.randomUUID(), key, name, type, tier, endpoint, "calendar:" + interval, enabled, interval.toSeconds(), 500,
                now, null, null, CalendarSourceHealth.HEALTHY, "calendar-v1", "", 0, 0, 0, 0, countries, categories, now, now);
    }

    private CalendarSource disabledSource(String key, String name, CalendarSourceType type, CalendarSourceTier tier, String endpoint, String warning, Instant now) {
        return new CalendarSource(UUID.randomUUID(), key, name, type, tier, endpoint, "manual", false, 3600, 100, now, null, null,
                CalendarSourceHealth.DISABLED, "calendar-v1", warning, 0, 0, 0, 0, Set.of(), Set.of(), now, now);
    }

    private static int sum(List<CalendarSourceResult> results, java.util.function.ToIntFunction<CalendarSourceResult> fn) {
        return results.stream().mapToInt(fn).sum();
    }

    private static String icsTime(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(instant);
    }

    private static String icsEscape(String value) {
        return nullSafe(value).replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private static String compactException(Throwable ex) {
        List<String> parts = new ArrayList<>();
        Throwable cursor = ex;
        while (cursor != null && parts.size() < 3) {
            String message = firstLine(cursor.getMessage());
            parts.add(cursor.getClass().getSimpleName() + (message.isBlank() ? "" : ":" + message));
            cursor = cursor.getCause();
        }
        return String.join(" <- ", parts);
    }
}
