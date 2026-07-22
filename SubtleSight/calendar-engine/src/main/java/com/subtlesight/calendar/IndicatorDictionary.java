package com.subtlesight.calendar;

import com.subtlesight.calendar.CalendarModels.CalendarCategory;
import com.subtlesight.calendar.CalendarModels.CalendarImportance;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IndicatorDictionary {
    private static final Pattern YEAR_MONTH = Pattern.compile("(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\s+(20\\d{2})\\b");
    private static final Pattern MONTH_YEAR_TAIL = Pattern.compile("(?i),\\s*([A-Za-z]+)\\s+(20\\d{2})\\b");
    private static final Pattern QUARTER = Pattern.compile("(?i)\\b(1st|2nd|3rd|4th|first|second|third|fourth|q[1-4])\\s+quarter(?:\\s+and\\s+year)?\\s+(20\\d{2})\\b");

    public NormalizedIndicator normalize(String originalName, String countryCode, String sourceKey) {
        String name = originalName == null ? "" : originalName;
        String lower = ascii(name).toLowerCase(Locale.ROOT);
        String country = normalizeCountry(countryCode, lower, sourceKey);
        CalendarCategory category = CalendarCategory.OTHER;
        String code = "UNMAPPED_" + slug(country + "_" + name);
        CalendarImportance importance = CalendarImportance.MEDIUM;
        String unit = "";
        String currency = currency(country);

        if (containsAny(lower, "consumer price", "cpi", "inflation")) {
            code = country + "_CPI";
            category = CalendarCategory.INFLATION;
            importance = CalendarImportance.HIGH;
            unit = "%";
        } else if (containsAny(lower, "producer price", "ppi")) {
            code = country + "_PPI";
            category = CalendarCategory.INFLATION;
            importance = CalendarImportance.MEDIUM;
            unit = "%";
        } else if (containsAny(lower, "employment situation", "nonfarm", "payroll", "unemployment", "jobless", "employment report", "labor market")) {
            code = country + "_EMPLOYMENT";
            category = CalendarCategory.EMPLOYMENT;
            importance = CalendarImportance.HIGH;
        } else if (containsAny(lower, "gross domestic product", "gdp")) {
            code = country + "_GDP";
            category = CalendarCategory.GROWTH;
            importance = CalendarImportance.HIGH;
            unit = "%";
        } else if (containsAny(lower, "personal income", "outlays", "pce")) {
            code = country + "_PCE";
            category = CalendarCategory.CONSUMER;
            importance = CalendarImportance.HIGH;
            unit = "%";
        } else if (containsAny(lower, "balance of trade", "international trade", "trade in goods", "trade balance", "import", "export")) {
            code = country + "_TRADE";
            category = CalendarCategory.TRADE;
            importance = CalendarImportance.MEDIUM;
        } else if (containsAny(lower, "retail sales", "consumer spending")) {
            code = country + "_RETAIL_SALES";
            category = CalendarCategory.CONSUMER;
            importance = CalendarImportance.HIGH;
            unit = "%";
        } else if (containsAny(lower, "industrial production", "manufacturing production", "ism manufacturing", "pmi", "purchasing managers")) {
            code = country + "_INDUSTRY";
            category = CalendarCategory.INDUSTRY;
            importance = CalendarImportance.HIGH;
        } else if (containsAny(lower, "housing starts", "building permits", "construction", "home sales")) {
            code = country + "_HOUSING";
            category = CalendarCategory.HOUSING;
        } else if (containsAny(lower, "fomc", "federal open market", "monetary policy", "interest rate", "rate decision", "governing council", "ecb")) {
            code = country + "_RATE_DECISION";
            category = CalendarCategory.CENTRAL_BANK;
            importance = CalendarImportance.HIGH;
            unit = "%";
        } else if (containsAny(lower, "speech", "remarks", "testimony")) {
            code = country + "_CENTRAL_BANK_SPEECH";
            category = CalendarCategory.SPEECH;
        }
        return new NormalizedIndicator(code, name, country, currency, category, importance, extractPeriod(name), unit);
    }

    public String normalizeCountry(String rawCountry, String lowerName, String sourceKey) {
        String raw = rawCountry == null ? "" : rawCountry.trim().toUpperCase(Locale.ROOT);
        if (raw.length() == 2 && raw.chars().allMatch(Character::isLetter)) return raw;
        String source = sourceKey == null ? "" : sourceKey.toLowerCase(Locale.ROOT);
        if (source.contains("nbs") || lowerName.contains("china")) return "CN";
        if (source.contains("ons") || lowerName.contains("uk ") || lowerName.contains("great britain") || lowerName.contains("england")) return "GB";
        if (source.contains("ecb") || lowerName.contains("euro area") || lowerName.contains("eurozone")) return "EU";
        if (source.contains("bea") || source.contains("bls") || source.contains("fred") || source.contains("nyfed") || source.contains("federalreserve") || lowerName.contains("u.s.") || lowerName.contains("united states")) return "US";
        return raw.isBlank() ? "UN" : raw;
    }

    public String extractPeriod(String name) {
        Matcher quarter = QUARTER.matcher(name == null ? "" : name);
        if (quarter.find()) {
            String raw = quarter.group(1).toLowerCase(Locale.ROOT);
            int q = switch (raw) {
                case "1st", "first", "q1" -> 1;
                case "2nd", "second", "q2" -> 2;
                case "3rd", "third", "q3" -> 3;
                default -> 4;
            };
            return quarter.group(2) + "-Q" + q;
        }
        Matcher month = YEAR_MONTH.matcher(name == null ? "" : name);
        if (month.find()) return month.group(2) + "-" + String.format("%02d", monthNumber(month.group(1)));
        month = MONTH_YEAR_TAIL.matcher(name == null ? "" : name);
        if (month.find()) return month.group(2) + "-" + String.format("%02d", monthNumber(month.group(1)));
        return "";
    }

    public CalendarImportance max(CalendarImportance a, CalendarImportance b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public record NormalizedIndicator(String code, String displayName, String countryCode, String currency,
                                      CalendarCategory category, CalendarImportance importance, String period, String unit) {}

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) if (haystack.contains(needle)) return true;
        return false;
    }

    private static int monthNumber(String month) {
        String key = month.toLowerCase(Locale.ROOT);
        return switch (key.substring(0, Math.min(3, key.length()))) {
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

    private static String currency(String country) {
        return switch (country) {
            case "US" -> "USD";
            case "CN" -> "CNY";
            case "GB" -> "GBP";
            case "EU" -> "EUR";
            case "JP" -> "JPY";
            case "CA" -> "CAD";
            case "AU" -> "AUD";
            default -> "";
        };
    }

    private static String slug(String value) {
        String normalized = ascii(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private static String ascii(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    }
}
