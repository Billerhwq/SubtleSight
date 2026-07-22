package com.subtlesight.calendar;

import com.subtlesight.calendar.CalendarModels.CalendarEventCandidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

public final class CalendarEventKeyFactory {
    private CalendarEventKeyFactory() {}

    public static String key(CalendarEventCandidate candidate) {
        String localDate = "unknown-date";
        if (candidate.scheduledAtUtc() != null) {
            ZoneId zone = zone(candidate.sourceTimezone());
            localDate = DateTimeFormatter.ISO_LOCAL_DATE.format(candidate.scheduledAtUtc().atZone(zone));
        } else if (!candidate.scheduledLocalText().isBlank()) {
            localDate = candidate.scheduledLocalText().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        }
        String seed = String.join("|",
                candidate.countryCode(),
                candidate.indicatorCode(),
                candidate.period() == null ? "" : candidate.period(),
                localDate);
        return sha256(seed).substring(0, 32);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 failed", ex);
        }
    }

    private static ZoneId zone(String value) {
        try {
            return ZoneId.of(value == null || value.isBlank() ? "UTC" : value);
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }
}
