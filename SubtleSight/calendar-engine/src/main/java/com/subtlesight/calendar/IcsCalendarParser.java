package com.subtlesight.calendar;

import com.subtlesight.calendar.CalendarModels.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IcsCalendarParser {
    private final IndicatorDictionary dictionary;

    public IcsCalendarParser(IndicatorDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public ParsedCalendarBatch parse(String ics, CalendarSource source) {
        List<String> warnings = new ArrayList<>();
        List<CalendarEventCandidate> candidates = new ArrayList<>();
        for (Map<String, String> event : vevents(ics)) {
            String summary = event.getOrDefault("SUMMARY", "").trim();
            if (summary.isBlank()) {
                warnings.add("VEVENT_WITHOUT_SUMMARY");
                continue;
            }
            String dt = firstValue(event, "DTSTART");
            Instant scheduled = parseInstant(dt, event.getOrDefault("TZID", sourceDefaultZone(source)), warnings);
            IndicatorDictionary.NormalizedIndicator normalized = dictionary.normalize(summary, countryFromSource(source), source.key());
            String url = extractUrl(event.getOrDefault("DESCRIPTION", ""));
            candidates.add(new CalendarEventCandidate(
                    event.getOrDefault("UID", summary + dt),
                    url.isBlank() ? source.endpoint() : url,
                    normalized.countryCode(),
                    "",
                    normalized.currency(),
                    summary,
                    summary,
                    normalized.code(),
                    normalized.category(),
                    normalized.importance(),
                    scheduled,
                    sourceDefaultZone(source),
                    dt,
                    normalized.period(),
                    null,
                    null,
                    null,
                    null,
                    normalized.unit(),
                    CalendarEventStatus.SCHEDULED,
                    url,
                    new LinkedHashMap<>(event),
                    List.of()));
        }
        if (candidates.isEmpty()) warnings.add("PARSER_DRIFT_EMPTY_ICS");
        return new ParsedCalendarBatch(candidates, warnings, source.parserVersion());
    }

    private static List<Map<String, String>> vevents(String content) {
        String unfolded = unfold(content == null ? "" : content);
        List<Map<String, String>> events = new ArrayList<>();
        Map<String, String> current = null;
        for (String line : unfolded.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("BEGIN:VEVENT")) {
                current = new LinkedHashMap<>();
            } else if (trimmed.equalsIgnoreCase("END:VEVENT")) {
                if (current != null) events.add(current);
                current = null;
            } else if (current != null) {
                int colon = trimmed.indexOf(':');
                if (colon <= 0) continue;
                String rawKey = trimmed.substring(0, colon);
                String key = rawKey.contains(";") ? rawKey.substring(0, rawKey.indexOf(';')) : rawKey;
                String value = unescape(trimmed.substring(colon + 1));
                current.put(key.toUpperCase(Locale.ROOT), value);
                if (rawKey.toUpperCase(Locale.ROOT).contains("TZID=")) {
                    current.put("TZID", rawKey.substring(rawKey.toUpperCase(Locale.ROOT).indexOf("TZID=") + 5).replace(";", ""));
                }
            }
        }
        return events;
    }

    private static String unfold(String content) {
        return content.replaceAll("\\r?\\n[ \\t]", "");
    }

    private static Instant parseInstant(String value, String zone, List<String> warnings) {
        if (value == null || value.isBlank()) {
            warnings.add("VEVENT_WITHOUT_DTSTART");
            return null;
        }
        try {
            if (value.endsWith("Z")) {
                String normalized = value.substring(0, value.length() - 1);
                DateTimeFormatter formatter = normalized.length() == 13 ? DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm") : DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
                return LocalDateTime.parse(normalized, formatter).atZone(ZoneId.of("UTC")).toInstant();
            }
            if (value.length() == 8) {
                return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atTime(LocalTime.NOON).atZone(ZoneId.of(zone)).toInstant();
            }
            DateTimeFormatter formatter = value.length() == 13 ? DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm") : DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
            return LocalDateTime.parse(value, formatter).atZone(ZoneId.of(zone)).toInstant();
        } catch (Exception ex) {
            warnings.add("BAD_DTSTART:" + value);
            return null;
        }
    }

    private static String firstValue(Map<String, String> event, String key) {
        return event.getOrDefault(key, "");
    }

    private static String unescape(String value) {
        return value.replace("\\,", ",").replace("\\n", " ").replace("\\;", ";").trim();
    }

    private static String extractUrl(String value) {
        try {
            for (String token : value.split("\\s+")) {
                if (token.startsWith("http://") || token.startsWith("https://")) return URI.create(token).toString();
                if (token.startsWith("www.")) return URI.create("https://" + token).toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String countryFromSource(CalendarSource source) {
        if (source.countries().size() == 1) return source.countries().iterator().next();
        return "";
    }

    private static String sourceDefaultZone(CalendarSource source) {
        return switch (source.key()) {
            case "bea-ics", "bls-ics", "fred-calendar", "nyfed-calendar", "federalreserve-fomc" -> "America/New_York";
            case "ons-release-calendar" -> "Europe/London";
            case "nbs-release-calendar" -> "Asia/Shanghai";
            case "ecb-meetings" -> "Europe/Berlin";
            default -> "UTC";
        };
    }
}
