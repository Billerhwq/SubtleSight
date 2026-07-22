package com.subtlesight.calendar.connectors;

import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.IndicatorDictionary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlCalendarSourceConnector implements CalendarSourceConnector {
    private static final Pattern NYFED_LINK = Pattern.compile("<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>[\\s\\S]{0,240}?\\((\\d{1,2}:\\d{2})\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RANGE = Pattern.compile("(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})(?:-(\\d{1,2}))?\\b");
    private static final DateTimeFormatter LONG_DATE = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM yyyy").toFormatter(Locale.ENGLISH);
    private final CalendarFetcher fetcher;
    private final IndicatorDictionary dictionary;

    public HtmlCalendarSourceConnector(CalendarFetcher fetcher, IndicatorDictionary dictionary) {
        this.fetcher = fetcher;
        this.dictionary = dictionary;
    }

    @Override public boolean supports(CalendarSource source) {
        return source.type() == CalendarSourceType.OFFICIAL_HTML || source.type() == CalendarSourceType.AGGREGATOR_HTML || source.type() == CalendarSourceType.CUSTOM_HTML;
    }

    @Override public CalendarFetch fetch(CalendarSource source) {
        return fetcher.fetch(URI.create(source.endpoint()));
    }

    @Override public ParsedCalendarBatch parse(CalendarSource source, CalendarFetch fetch) {
        if (fetch.status() < 200 || fetch.status() >= 300) {
            return new ParsedCalendarBatch(List.of(), List.of("HTTP_" + fetch.status()), source.parserVersion());
        }
        String html = text(fetch);
        Document doc = Jsoup.parse(html, source.endpoint());
        List<String> warnings = new ArrayList<>();
        List<CalendarEventCandidate> candidates = switch (source.key()) {
            case "ons-release-calendar" -> parseOns(doc, source, warnings);
            case "nbs-release-calendar" -> parseNbs(doc, source, warnings);
            case "nyfed-calendar" -> parseNyFed(doc, source, warnings);
            case "federalreserve-fomc" -> parseFed(doc, source, warnings);
            case "ecb-meetings" -> parseEcb(doc, source, warnings);
            case "tradingeconomics-calendar" -> parseTradingEconomics(doc, source, warnings);
            case "bea-html" -> parseBeaHtml(doc, source, warnings);
            default -> parseGenericTables(doc, source, warnings);
        };
        if (candidates.isEmpty()) warnings.add("PARSER_DRIFT_EMPTY_HTML");
        return new ParsedCalendarBatch(candidates, warnings, source.parserVersion());
    }

    private List<CalendarEventCandidate> parseOns(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        for (Element link : doc.select("a[data-gtm-release-title][data-gtm-release-date]")) {
            String title = link.attr("data-gtm-release-title");
            String date = link.attr("data-gtm-release-date");
            String time = link.attr("data-gtm-release-time");
            Instant at = parseYmdTime(date, time, ZoneId.of("Europe/London"), warnings);
            String statusText = link.parent() == null ? "" : link.parent().text().toLowerCase(Locale.ROOT);
            CalendarEventStatus status = statusText.contains("published") ? CalendarEventStatus.RELEASED : CalendarEventStatus.SCHEDULED;
            out.add(candidate(source, "ons-" + date + "-" + slug(title), link.absUrl("href"), title, "GB", at, "Europe/London", date + " " + time, null, null, null, null, status, Map.of("date", date, "time", time, "statusText", statusText)));
        }
        return out;
    }

    private List<CalendarEventCandidate> parseNbs(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        int year = yearFromText(doc.title(), Year.now(ZoneOffset.UTC).getValue());
        Elements rows = doc.select("table tr");
        for (int i = 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i).select("td").eachText().stream().map(this::clean).toList();
            if (cells.size() < 14 || !cells.get(0).matches("\\d+")) continue;
            String title = cells.get(1);
            List<String> times = i + 1 < rows.size() ? rows.get(i + 1).select("td").eachText().stream().map(this::clean).toList() : List.of();
            for (int month = 1; month <= 12 && month + 1 < cells.size(); month++) {
                String dayText = cells.get(month + 1);
                if (!dayText.matches(".*\\d{1,2}/.*")) continue;
                int day = Integer.parseInt(dayText.replaceAll("[^0-9].*", ""));
                String time = month - 1 < times.size() ? times.get(month - 1) : "10:00";
                LocalTime localTime = parseLocalTime(time, LocalTime.of(10, 0));
                Instant at = LocalDate.of(year, month, day).atTime(localTime).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
                out.add(candidate(source, "nbs-" + year + "-" + month + "-" + day + "-" + slug(title), source.endpoint(), title,
                        "CN", at, "Asia/Shanghai", dayText + " " + time, null, null, null, null, CalendarEventStatus.SCHEDULED,
                        Map.of("month", month, "dayText", dayText, "time", time)));
            }
        }
        return out;
    }

    private List<CalendarEventCandidate> parseNyFed(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        YearMonth ym = parseYearMonth(doc.text(), YearMonth.now(ZoneOffset.UTC));
        for (Element cell : doc.select("td.somatdR.dirColL")) {
            String html = cell.html();
            Matcher dayMatcher = Pattern.compile("(?s)<div>\\s*(\\d{1,2})").matcher(html);
            if (!dayMatcher.find()) continue;
            int day = Integer.parseInt(dayMatcher.group(1));
            Matcher eventMatcher = NYFED_LINK.matcher(html);
            while (eventMatcher.find()) {
                String href = eventMatcher.group(1);
                String title = Jsoup.parse(eventMatcher.group(2)).text();
                LocalTime time = parseLocalTime(eventMatcher.group(3), LocalTime.of(10, 0));
                Instant at = ym.atDay(day).atTime(time).atZone(ZoneId.of("America/New_York")).toInstant();
                out.add(candidate(source, "nyfed-" + ym + "-" + day + "-" + slug(title), abs(source.endpoint(), href), title,
                        "US", at, "America/New_York", ym + " " + day + " " + time, null, null, null, null, CalendarEventStatus.SCHEDULED,
                        Map.of("href", href, "time", time.toString())));
            }
        }
        return out;
    }

    private List<CalendarEventCandidate> parseFed(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element row : doc.select(".fomc-meeting")) {
            Element panel = row.closest(".panel");
            int year = yearFromText(panel == null ? row.text() : panel.select(".panel-heading").text(), Year.now(ZoneOffset.UTC).getValue());
            Element monthElement = row.selectFirst(".fomc-meeting__month");
            Element dateElement = row.selectFirst(".fomc-meeting__date");
            if (monthElement == null || dateElement == null) continue;
            Matcher dayMatcher = Pattern.compile("(\\d{1,2})(?:-(\\d{1,2}))?").matcher(dateElement.text());
            if (!dayMatcher.find()) continue;
            int month = month(monthElement.text());
            int day = Integer.parseInt(dayMatcher.group(2) == null ? dayMatcher.group(1) : dayMatcher.group(2));
            String key = year + "-" + month + "-" + day;
            if (!seen.add(key)) continue;
            Instant at = LocalDate.of(year, month, day).atTime(14, 0).atZone(ZoneId.of("America/New_York")).toInstant();
            String statement = row.select("a[href*=pressreleases],a[href*=monetary]").stream()
                    .map(a -> a.absUrl("href")).filter(v -> !v.isBlank()).findFirst().orElse(source.endpoint());
            out.add(candidate(source, "fed-fomc-" + key, source.endpoint(), "FOMC Meeting / Monetary Policy Decision",
                    "US", at, "America/New_York", monthElement.text() + " " + dateElement.text(), null, null, null, null, CalendarEventStatus.SCHEDULED,
                    Map.of("year", year, "month", monthElement.text(), "date", dateElement.text(), "statement", statement)));
        }
        return out;
    }

    private List<CalendarEventCandidate> parseEcb(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?i)(\\d{1,2}\\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+20\\d{2})[^.]{0,180}(monetary policy|Governing Council|press conference)").matcher(doc.text());
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            LocalDate date = parseLongDate(matcher.group(1), warnings);
            if (date == null || !seen.add(date.toString())) continue;
            Instant at = date.atTime(14, 15).atZone(ZoneId.of("Europe/Berlin")).toInstant();
            out.add(candidate(source, "ecb-" + date, source.endpoint(), "ECB Governing Council monetary policy meeting",
                    "EU", at, "Europe/Berlin", matcher.group(1), null, null, null, null, CalendarEventStatus.SCHEDULED,
                    Map.of("matchedDate", matcher.group(1), "context", matcher.group(2))));
        }
        return out;
    }

    private List<CalendarEventCandidate> parseTradingEconomics(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        for (Element row : doc.select("tr[data-id][data-country][data-event]")) {
            String date = "";
            Element dateCell = row.selectFirst("td[class~=\\d{4}-\\d{2}-\\d{2}]");
            if (dateCell != null) {
                Matcher m = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})").matcher(dateCell.className());
                if (m.find()) date = m.group(1);
            }
            String time = row.selectFirst(".calendar-date-1") == null ? "" : row.selectFirst(".calendar-date-1").text();
            Instant at = parseIsoDateTime(date, time, ZoneId.of("UTC"), warnings);
            String country = countryFromIso(row.selectFirst(".calendar-iso") == null ? "" : row.selectFirst(".calendar-iso").text(), row.attr("data-country"));
            String event = row.attr("data-event");
            String url = abs("https://tradingeconomics.com/calendar", row.attr("data-url"));
            String actual = textOf(row, "span#actual");
            String forecast = textOf(row, "a#forecast");
            String previous = textOf(row, "span#previous");
            out.add(candidate(source, "te-" + row.attr("data-id"), url, capitalize(event), country, at, "UTC", date + " " + time,
                    actual, forecast, previous, revised(row), CalendarEventStatus.CONFIRMED,
                    raw("country", row.attr("data-country"), "category", row.attr("data-category"), "symbol", row.attr("data-symbol"),
                            "actual", actual, "forecast", forecast, "previous", previous)));
        }
        return out;
    }

    private List<CalendarEventCandidate> parseBeaHtml(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        for (Element row : doc.select("tr")) {
            Element date = row.selectFirst(".release-date");
            Element title = row.selectFirst(".release-title");
            if (date == null || title == null) continue;
            int year = yearFromText(row.text() + " " + doc.text(), Year.now(ZoneOffset.UTC).getValue());
            String time = row.selectFirst("small") == null ? "8:30 AM" : row.selectFirst("small").text();
            Instant at = parseMonthDay(year, date.text(), time, ZoneId.of("America/New_York"), warnings);
            String url = row.selectFirst("a[href]") == null ? source.endpoint() : row.selectFirst("a[href]").absUrl("href");
            out.add(candidate(source, "bea-html-" + year + "-" + slug(date.text() + title.text()), url, title.text(), "US", at,
                    "America/New_York", date.text() + " " + time, null, null, null, null, CalendarEventStatus.SCHEDULED,
                    Map.of("date", date.text(), "time", time)));
        }
        return out;
    }

    private List<CalendarEventCandidate> parseGenericTables(Document doc, CalendarSource source, List<String> warnings) {
        List<CalendarEventCandidate> out = new ArrayList<>();
        for (Element row : doc.select("tr")) {
            String text = row.text();
            Matcher date = DATE_RANGE.matcher(text);
            if (!date.find()) continue;
            String title = text.replace(date.group(), "").trim();
            if (title.length() < 5) continue;
            Instant at = parseMonthDay(Year.now(ZoneOffset.UTC).getValue(), date.group(), "09:00", ZoneId.of("UTC"), warnings);
            out.add(candidate(source, "generic-" + slug(text), source.endpoint(), title, "", at, "UTC", date.group(), null, null, null, null, CalendarEventStatus.SCHEDULED, Map.of("row", text)));
        }
        return out;
    }

    private CalendarEventCandidate candidate(CalendarSource source, String sourceEventId, String url, String title, String country, Instant at,
                                             String zone, String localText, String actual, String forecast, String previous, String revised,
                                             CalendarEventStatus status, Map<String, Object> raw) {
        IndicatorDictionary.NormalizedIndicator n = dictionary.normalize(title, country, source.key());
        String normalizedCountry = dictionary.normalizeCountry(country, title.toLowerCase(Locale.ROOT), source.key());
        CalendarImportance importance = source.tier() == CalendarSourceTier.AGGREGATOR ? n.importance() : n.importance();
        return new CalendarEventCandidate(sourceEventId, url, normalizedCountry, "", n.currency(), title, title, n.code(), n.category(), importance,
                at, zone, localText, n.period(), actual, forecast, previous, revised, n.unit(), status, source.tier() == CalendarSourceTier.OFFICIAL ? url : "",
                raw, List.of());
    }

    private Instant parseYmdTime(String ymd, String time, ZoneId zone, List<String> warnings) {
        try {
            LocalDate date = LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE);
            return date.atTime(parseLocalTime(time, LocalTime.of(9, 30))).atZone(zone).toInstant();
        } catch (Exception ex) {
            warnings.add("BAD_DATE:" + ymd + " " + time);
            return null;
        }
    }

    private Instant parseIsoDateTime(String date, String time, ZoneId zone, List<String> warnings) {
        try {
            if (date == null || date.isBlank()) return null;
            return LocalDate.parse(date).atTime(parseLocalTime(time, LocalTime.NOON)).atZone(zone).toInstant();
        } catch (Exception ex) {
            warnings.add("BAD_DATE:" + date + " " + time);
            return null;
        }
    }

    private Instant parseMonthDay(int year, String monthDay, String time, ZoneId zone, List<String> warnings) {
        try {
            Matcher m = DATE_RANGE.matcher(monthDay);
            if (!m.find()) return null;
            int day = Integer.parseInt(m.group(3) == null ? m.group(2) : m.group(3));
            return LocalDate.of(year, month(m.group(1)), day).atTime(parseLocalTime(time, LocalTime.of(9, 0))).atZone(zone).toInstant();
        } catch (Exception ex) {
            warnings.add("BAD_DATE:" + monthDay + " " + time);
            return null;
        }
    }

    private LocalDate parseLongDate(String value, List<String> warnings) {
        try {
            return LocalDate.parse(value, LONG_DATE);
        } catch (Exception ex) {
            warnings.add("BAD_DATE:" + value);
            return null;
        }
    }

    private LocalTime parseLocalTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank() || raw.equals("&nbsp;")) return fallback;
        String cleaned = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        try {
            if (cleaned.matches("\\d{1,2}:\\d{2}")) return LocalTime.parse(cleaned.length() == 4 ? "0" + cleaned : cleaned);
            if (cleaned.matches("\\d{1,2}:\\d{2}\\s*[AP]M")) return LocalTime.parse(cleaned.replace(" ", ""), DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH));
            if (cleaned.matches("\\d{1,2}\\s*[AP]M")) return LocalTime.parse(cleaned.replace(" ", ""), DateTimeFormatter.ofPattern("ha", Locale.ENGLISH));
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private YearMonth parseYearMonth(String text, YearMonth fallback) {
        Matcher m = Pattern.compile("(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(20\\d{2})\\b").matcher(text);
        if (!m.find()) return fallback;
        return YearMonth.of(Integer.parseInt(m.group(2)), month(m.group(1)));
    }

    private int yearFromText(String text, int fallback) {
        Matcher m = Pattern.compile("\\b(20\\d{2})\\b").matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    private int month(String value) {
        return switch (value.substring(0, Math.min(3, value.length())).toLowerCase(Locale.ROOT)) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> 1;
        };
    }

    private String textOf(Element root, String selector) {
        Element value = root.selectFirst(selector);
        return value == null ? null : clean(value.text());
    }

    private String revised(Element row) {
        Element revised = row.selectFirst("span#revised");
        if (revised == null) return null;
        String title = revised.attr("title");
        int idx = title.toLowerCase(Locale.ROOT).lastIndexOf("from ");
        return idx < 0 ? null : title.substring(idx + 5).trim();
    }

    private String countryFromIso(String iso, String name) {
        String cleaned = clean(iso).toUpperCase(Locale.ROOT);
        if (cleaned.length() == 2) return cleaned;
        return dictionary.normalizeCountry("", name == null ? "" : name.toLowerCase(Locale.ROOT), "");
    }

    private Map<String, Object> raw(Object... values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object value = values[i + 1];
            if (value != null) out.put(String.valueOf(values[i]), value);
        }
        return out;
    }

    private String capitalize(String value) {
        String text = clean(value);
        if (text.isBlank()) return text;
        StringBuilder out = new StringBuilder();
        for (String part : text.split("\\s+")) {
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        return out.toString().trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String slug(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String abs(String base, String href) {
        try {
            return URI.create(base).resolve(href).toString();
        } catch (Exception ignored) {
            return href == null ? base : href;
        }
    }
}
