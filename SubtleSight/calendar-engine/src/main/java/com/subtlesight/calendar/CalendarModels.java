package com.subtlesight.calendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CalendarModels {
    private CalendarModels() {}

    public enum CalendarSourceType { OFFICIAL_ICS, OFFICIAL_HTML, OFFICIAL_RSS, AGGREGATOR_HTML, AGGREGATOR_JSON, CUSTOM_HTML }
    public enum CalendarSourceTier { OFFICIAL, CROSS_CHECK, AGGREGATOR }
    public enum CalendarSourceHealth { HEALTHY, DEGRADED, DOWN, DISABLED }
    public enum CalendarImportance { LOW, MEDIUM, HIGH }
    public enum CalendarCategory { INFLATION, EMPLOYMENT, GROWTH, TRADE, CENTRAL_BANK, SPEECH, HOUSING, CONSUMER, INDUSTRY, HOLIDAY, OTHER }
    public enum CalendarEventStatus { SCHEDULED, CONFIRMED, RELEASED, REVISED, DELAYED, CANCELLED }

    public record CalendarSource(
            UUID id,
            String key,
            String name,
            CalendarSourceType type,
            CalendarSourceTier tier,
            String endpoint,
            String schedule,
            boolean enabled,
            long minIntervalSeconds,
            int dailyBudget,
            Instant nextAllowedAt,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            CalendarSourceHealth health,
            String parserVersion,
            String warning,
            int lastHttpStatus,
            int lastParsedCount,
            int lastInsertedCount,
            int lastUpdatedCount,
            Set<String> countries,
            Set<String> categories,
            Instant createdAt,
            Instant updatedAt) {
        public CalendarSource {
            Objects.requireNonNull(id);
            key = requireText(key, "source key");
            name = requireText(name, "source name");
            Objects.requireNonNull(type);
            Objects.requireNonNull(tier);
            endpoint = requireText(endpoint, "source endpoint");
            schedule = schedule == null ? "manual" : schedule;
            health = health == null ? CalendarSourceHealth.HEALTHY : health;
            parserVersion = parserVersion == null || parserVersion.isBlank() ? "calendar-v1" : parserVersion;
            warning = warning == null ? "" : warning;
            countries = countries == null ? Set.of() : Set.copyOf(countries);
            categories = categories == null ? Set.of() : Set.copyOf(categories);
            Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
        }
    }

    public record RawCalendarSnapshot(
            UUID id,
            UUID sourceId,
            String sourceKey,
            String url,
            String finalUrl,
            int httpStatus,
            String contentType,
            String etag,
            String lastModified,
            Instant fetchedAt,
            String contentHash,
            long contentLength,
            String parserVersion,
            String body) {
        public RawCalendarSnapshot {
            Objects.requireNonNull(id);
            Objects.requireNonNull(sourceId);
            sourceKey = requireText(sourceKey, "source key");
            url = requireText(url, "snapshot url");
            finalUrl = finalUrl == null || finalUrl.isBlank() ? url : finalUrl;
            contentType = contentType == null ? "application/octet-stream" : contentType;
            etag = etag == null ? "" : etag;
            lastModified = lastModified == null ? "" : lastModified;
            Objects.requireNonNull(fetchedAt);
            contentHash = requireText(contentHash, "content hash");
            parserVersion = parserVersion == null || parserVersion.isBlank() ? "calendar-v1" : parserVersion;
            body = body == null ? "" : body;
        }
    }

    public record CalendarEvent(
            UUID id,
            String eventKey,
            String indicatorCode,
            String nameZh,
            String nameOriginal,
            String countryCode,
            String region,
            String currency,
            CalendarCategory category,
            CalendarImportance importance,
            String importanceSource,
            Instant scheduledAtUtc,
            String sourceTimezone,
            String scheduledLocalText,
            String period,
            String actual,
            String forecast,
            String previous,
            String revisedPrevious,
            String unit,
            CalendarEventStatus status,
            String officialUrl,
            UUID primarySourceId,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant releasedAt,
            int version) {
        public CalendarEvent {
            Objects.requireNonNull(id);
            eventKey = requireText(eventKey, "event key");
            indicatorCode = requireText(indicatorCode, "indicator code");
            nameZh = nameZh == null || nameZh.isBlank() ? nameOriginal : nameZh;
            nameOriginal = requireText(nameOriginal, "event name");
            countryCode = countryCode == null || countryCode.isBlank() ? "UN" : countryCode.toUpperCase();
            region = region == null ? "" : region;
            currency = currency == null ? "" : currency;
            category = category == null ? CalendarCategory.OTHER : category;
            importance = importance == null ? CalendarImportance.MEDIUM : importance;
            importanceSource = importanceSource == null ? "" : importanceSource;
            sourceTimezone = sourceTimezone == null ? "UTC" : sourceTimezone;
            scheduledLocalText = scheduledLocalText == null ? "" : scheduledLocalText;
            period = period == null ? "" : period;
            actual = blankToNull(actual);
            forecast = blankToNull(forecast);
            previous = blankToNull(previous);
            revisedPrevious = blankToNull(revisedPrevious);
            unit = unit == null ? "" : unit;
            status = status == null ? CalendarEventStatus.SCHEDULED : status;
            officialUrl = officialUrl == null ? "" : officialUrl;
            Objects.requireNonNull(firstSeenAt);
            Objects.requireNonNull(lastSeenAt);
        }
    }

    public record CalendarEventEvidence(
            UUID id,
            UUID eventId,
            UUID sourceId,
            UUID rawSnapshotId,
            String sourceEventId,
            String sourceUrl,
            Instant fetchedAt,
            String parserVersion,
            String rawFieldsJson,
            String warningsJson,
            CalendarSourceTier sourceTier) {
        public CalendarEventEvidence {
            Objects.requireNonNull(id);
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(rawSnapshotId);
            sourceEventId = sourceEventId == null ? "" : sourceEventId;
            sourceUrl = sourceUrl == null ? "" : sourceUrl;
            Objects.requireNonNull(fetchedAt);
            parserVersion = parserVersion == null ? "calendar-v1" : parserVersion;
            rawFieldsJson = rawFieldsJson == null ? "{}" : rawFieldsJson;
            warningsJson = warningsJson == null ? "[]" : warningsJson;
            sourceTier = sourceTier == null ? CalendarSourceTier.CROSS_CHECK : sourceTier;
        }
    }

    public record CalendarEventRevision(
            UUID id,
            UUID eventId,
            Instant changedAt,
            UUID sourceId,
            String changedFieldsJson,
            String reason) {
        public CalendarEventRevision {
            Objects.requireNonNull(id);
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(changedAt);
            changedFieldsJson = changedFieldsJson == null ? "{}" : changedFieldsJson;
            reason = reason == null ? "" : reason;
        }
    }

    public record CalendarEventCandidate(
            String sourceEventId,
            String sourceUrl,
            String countryCode,
            String region,
            String currency,
            String nameOriginal,
            String nameZh,
            String indicatorCode,
            CalendarCategory category,
            CalendarImportance importance,
            Instant scheduledAtUtc,
            String sourceTimezone,
            String scheduledLocalText,
            String period,
            String actual,
            String forecast,
            String previous,
            String revisedPrevious,
            String unit,
            CalendarEventStatus status,
            String officialUrl,
            Map<String, Object> rawFields,
            List<String> warnings) {
        public CalendarEventCandidate {
            sourceEventId = sourceEventId == null ? "" : sourceEventId;
            sourceUrl = sourceUrl == null ? "" : sourceUrl;
            countryCode = countryCode == null || countryCode.isBlank() ? "UN" : countryCode.toUpperCase();
            region = region == null ? "" : region;
            currency = currency == null ? "" : currency;
            nameOriginal = requireText(nameOriginal, "candidate event name");
            nameZh = nameZh == null || nameZh.isBlank() ? nameOriginal : nameZh;
            indicatorCode = indicatorCode == null || indicatorCode.isBlank() ? "UNMAPPED" : indicatorCode;
            category = category == null ? CalendarCategory.OTHER : category;
            importance = importance == null ? CalendarImportance.MEDIUM : importance;
            sourceTimezone = sourceTimezone == null ? "UTC" : sourceTimezone;
            scheduledLocalText = scheduledLocalText == null ? "" : scheduledLocalText;
            period = period == null ? "" : period;
            actual = blankToNull(actual);
            forecast = blankToNull(forecast);
            previous = blankToNull(previous);
            revisedPrevious = blankToNull(revisedPrevious);
            unit = unit == null ? "" : unit;
            status = status == null ? CalendarEventStatus.SCHEDULED : status;
            officialUrl = officialUrl == null ? "" : officialUrl;
            rawFields = rawFields == null ? Map.of() : Map.copyOf(rawFields);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record ParsedCalendarBatch(List<CalendarEventCandidate> candidates, List<String> warnings, String parserVersion) {
        public ParsedCalendarBatch {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            parserVersion = parserVersion == null || parserVersion.isBlank() ? "calendar-v1" : parserVersion;
        }
    }

    public record CalendarSourceResult(
            UUID sourceId,
            String sourceKey,
            String sourceName,
            boolean ok,
            int httpStatus,
            int parsed,
            int inserted,
            int updated,
            int unchanged,
            String warning,
            Instant attemptedAt,
            Instant successAt,
            Instant nextAllowedAt,
            String contentHash) {}

    public record CalendarRefreshReport(
            Instant startedAt,
            Instant finishedAt,
            int totalSources,
            int totalParsed,
            int totalInserted,
            int totalUpdated,
            int totalUnchanged,
            List<CalendarSourceResult> results) {
        public CalendarRefreshReport {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record CalendarQuery(
            Instant from,
            Instant to,
            Set<String> countries,
            Set<CalendarCategory> categories,
            Set<CalendarImportance> importance,
            Set<CalendarEventStatus> status,
            int limit) {
        public CalendarQuery {
            countries = countries == null ? Set.of() : Set.copyOf(countries);
            categories = categories == null ? Set.of() : Set.copyOf(categories);
            importance = importance == null ? Set.of() : Set.copyOf(importance);
            status = status == null ? Set.of() : Set.copyOf(status);
            if (limit <= 0) limit = 200;
        }
    }

    public record CalendarEventDetail(CalendarEvent event, List<CalendarEventEvidence> evidence, List<CalendarEventRevision> revisions) {
        public CalendarEventDetail {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            revisions = revisions == null ? List.of() : List.copyOf(revisions);
        }
    }

    public record CalendarMergeResult(int inserted, int updated, int unchanged, List<CalendarEvent> events) {
        public CalendarMergeResult {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    public record CalendarSurprise(String direction, BigDecimal difference, boolean comparable) {}

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || value.equals("-") || value.equals("—") ? null : value.trim();
    }
}
