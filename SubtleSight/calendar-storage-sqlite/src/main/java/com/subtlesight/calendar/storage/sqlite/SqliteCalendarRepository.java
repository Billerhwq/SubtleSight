package com.subtlesight.calendar.storage.sqlite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.calendar.CalendarModels.*;
import com.subtlesight.calendar.CalendarRepository;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class SqliteCalendarRepository implements CalendarRepository {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SqliteCalendarRepository(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = JdbcClient.create(dataSource);
        this.json = mapper.copy().findAndRegisterModules();
    }

    @Override public CalendarSource saveSource(CalendarSource s) {
        jdbc.sql("""
                INSERT INTO calendar_sources(id,source_key,name,type,tier,endpoint,schedule,enabled,min_interval_seconds,daily_budget,
                next_allowed_at,last_attempt_at,last_success_at,health,parser_version,warning,last_http_status,last_parsed_count,
                last_inserted_count,last_updated_count,countries_json,categories_json,created_at,updated_at)
                VALUES(:id,:key,:name,:type,:tier,:endpoint,:schedule,:enabled,:interval,:budget,:next,:attempt,:success,:health,
                :parser,:warning,:http,:parsed,:inserted,:updated,:countries,:categories,:created,:updatedAt)
                ON CONFLICT(source_key) DO UPDATE SET name=excluded.name,type=excluded.type,tier=excluded.tier,endpoint=excluded.endpoint,
                schedule=excluded.schedule,enabled=excluded.enabled,min_interval_seconds=excluded.min_interval_seconds,
                daily_budget=excluded.daily_budget,
                parser_version=excluded.parser_version,countries_json=excluded.countries_json,categories_json=excluded.categories_json,
                updated_at=excluded.updated_at
                """).params(Map.ofEntries(
                entry("id", s.id()), entry("key", s.key()), entry("name", s.name()), entry("type", s.type()), entry("tier", s.tier()),
                entry("endpoint", s.endpoint()), entry("schedule", s.schedule()), entry("enabled", s.enabled()), entry("interval", s.minIntervalSeconds()),
                entry("budget", s.dailyBudget()), entry("next", s.nextAllowedAt()), entry("attempt", s.lastAttemptAt()), entry("success", s.lastSuccessAt()),
                entry("health", s.health()), entry("parser", s.parserVersion()), entry("warning", s.warning()), entry("http", s.lastHttpStatus()),
                entry("parsed", s.lastParsedCount()), entry("inserted", s.lastInsertedCount()), entry("updated", s.lastUpdatedCount()),
                entry("countries", write(s.countries())), entry("categories", write(s.categories())), entry("created", s.createdAt()),
                entry("updatedAt", s.updatedAt()))).update();
        return findSourceByKey(s.key()).orElse(s);
    }

    @Override public Optional<CalendarSource> findSource(UUID id) {
        return jdbc.sql("SELECT * FROM calendar_sources WHERE id=:id").param("id", id.toString()).query(this::source).optional();
    }

    @Override public Optional<CalendarSource> findSourceByKey(String key) {
        return jdbc.sql("SELECT * FROM calendar_sources WHERE source_key=:key").param("key", key).query(this::source).optional();
    }

    @Override public List<CalendarSource> listSources() {
        return jdbc.sql("SELECT * FROM calendar_sources ORDER BY tier,name").query(this::source).list();
    }

    @Override public RawCalendarSnapshot saveSnapshot(RawCalendarSnapshot s) {
        jdbc.sql("""
                INSERT INTO calendar_raw_snapshots(id,source_id,source_key,url,final_url,http_status,content_type,etag,last_modified,
                fetched_at,content_hash,content_length,parser_version,body)
                VALUES(:id,:source,:key,:url,:final,:http,:type,:etag,:modified,:fetched,:hash,:length,:parser,:body)
                ON CONFLICT(source_id,content_hash) DO NOTHING
                """).params(Map.ofEntries(
                entry("id", s.id()), entry("source", s.sourceId()), entry("key", s.sourceKey()), entry("url", s.url()), entry("final", s.finalUrl()),
                entry("http", s.httpStatus()), entry("type", s.contentType()), entry("etag", s.etag()), entry("modified", s.lastModified()),
                entry("fetched", s.fetchedAt()), entry("hash", s.contentHash()), entry("length", s.contentLength()), entry("parser", s.parserVersion()),
                entry("body", s.body()))).update();
        return jdbc.sql("SELECT * FROM calendar_raw_snapshots WHERE source_id=:source AND content_hash=:hash")
                .param("source", s.sourceId().toString()).param("hash", s.contentHash()).query(this::snapshot).single();
    }

    @Override public Optional<CalendarEvent> findEvent(UUID id) {
        return jdbc.sql("SELECT * FROM calendar_events WHERE id=:id").param("id", id.toString()).query(this::event).optional();
    }

    @Override public Optional<CalendarEvent> findEventByKey(String eventKey) {
        return jdbc.sql("SELECT * FROM calendar_events WHERE event_key=:key").param("key", eventKey).query(this::event).optional();
    }

    @Override public CalendarEvent saveEvent(CalendarEvent e) {
        jdbc.sql("""
                INSERT INTO calendar_events(id,event_key,indicator_code,name_zh,name_original,country_code,region,currency,category,
                importance,importance_source,scheduled_at_utc,source_timezone,scheduled_local_text,period,actual,forecast,previous,
                revised_previous,unit,status,official_url,primary_source_id,first_seen_at,last_seen_at,released_at,version)
                VALUES(:id,:key,:indicator,:zh,:original,:country,:region,:currency,:category,:importance,:importanceSource,:scheduled,
                :timezone,:localText,:period,:actual,:forecast,:previous,:revised,:unit,:status,:url,:source,:first,:last,:released,:version)
                ON CONFLICT(event_key) DO UPDATE SET name_zh=excluded.name_zh,name_original=excluded.name_original,importance=excluded.importance,
                importance_source=excluded.importance_source,scheduled_at_utc=excluded.scheduled_at_utc,scheduled_local_text=excluded.scheduled_local_text,
                actual=excluded.actual,forecast=excluded.forecast,previous=excluded.previous,revised_previous=excluded.revised_previous,
                unit=excluded.unit,status=excluded.status,official_url=excluded.official_url,primary_source_id=excluded.primary_source_id,
                last_seen_at=excluded.last_seen_at,released_at=excluded.released_at,version=excluded.version
                """).params(Map.ofEntries(
                entry("id", e.id()), entry("key", e.eventKey()), entry("indicator", e.indicatorCode()), entry("zh", e.nameZh()),
                entry("original", e.nameOriginal()), entry("country", e.countryCode()), entry("region", e.region()), entry("currency", e.currency()),
                entry("category", e.category()), entry("importance", e.importance()), entry("importanceSource", e.importanceSource()),
                entry("scheduled", e.scheduledAtUtc()), entry("timezone", e.sourceTimezone()), entry("localText", e.scheduledLocalText()),
                entry("period", e.period()), entry("actual", e.actual()), entry("forecast", e.forecast()), entry("previous", e.previous()),
                entry("revised", e.revisedPrevious()), entry("unit", e.unit()), entry("status", e.status()), entry("url", e.officialUrl()),
                entry("source", e.primarySourceId()), entry("first", e.firstSeenAt()), entry("last", e.lastSeenAt()), entry("released", e.releasedAt()),
                entry("version", e.version()))).update();
        return findEventByKey(e.eventKey()).orElse(e);
    }

    @Override public CalendarEventEvidence saveEvidence(CalendarEventEvidence e) {
        jdbc.sql("""
                INSERT INTO calendar_event_evidence(id,event_id,source_id,raw_snapshot_id,source_event_id,source_url,fetched_at,
                parser_version,raw_fields_json,warnings_json,source_tier)
                VALUES(:id,:event,:source,:raw,:sourceEvent,:url,:fetched,:parser,:fields,:warnings,:tier)
                ON CONFLICT(event_id,source_id,raw_snapshot_id,source_event_id) DO NOTHING
                """).params(Map.ofEntries(entry("id", e.id()), entry("event", e.eventId()), entry("source", e.sourceId()), entry("raw", e.rawSnapshotId()),
                entry("sourceEvent", e.sourceEventId()), entry("url", e.sourceUrl()), entry("fetched", e.fetchedAt()), entry("parser", e.parserVersion()),
                entry("fields", e.rawFieldsJson()), entry("warnings", e.warningsJson()), entry("tier", e.sourceTier()))).update();
        return e;
    }

    @Override public CalendarEventRevision saveRevision(CalendarEventRevision r) {
        jdbc.sql("INSERT INTO calendar_event_revisions(id,event_id,changed_at,source_id,changed_fields_json,reason) VALUES(:id,:event,:changed,:source,:fields,:reason)")
                .params(Map.ofEntries(entry("id", r.id()), entry("event", r.eventId()), entry("changed", r.changedAt()), entry("source", r.sourceId()),
                        entry("fields", r.changedFieldsJson()), entry("reason", r.reason()))).update();
        return r;
    }

    @Override public List<CalendarEvent> listEvents(CalendarQuery query) {
        List<CalendarEvent> rows = jdbc.sql("""
                SELECT * FROM calendar_events
                WHERE (:from IS NULL OR scheduled_at_utc IS NULL OR scheduled_at_utc >= :from)
                AND (:to IS NULL OR scheduled_at_utc IS NULL OR scheduled_at_utc <= :to)
                ORDER BY scheduled_at_utc IS NULL, scheduled_at_utc ASC, importance DESC, country_code
                LIMIT :limit
                """).param("from", query.from() == null ? null : query.from().toString())
                .param("to", query.to() == null ? null : query.to().toString())
                .param("limit", Math.max(query.limit() * 4, query.limit()))
                .query(this::event).list();
        return rows.stream()
                .filter(e -> query.countries().isEmpty() || query.countries().contains(e.countryCode()))
                .filter(e -> query.categories().isEmpty() || query.categories().contains(e.category()))
                .filter(e -> query.importance().isEmpty() || query.importance().contains(e.importance()))
                .filter(e -> query.status().isEmpty() || query.status().contains(e.status()))
                .limit(query.limit())
                .collect(Collectors.toList());
    }

    @Override public List<CalendarEventEvidence> listEvidence(UUID eventId) {
        return jdbc.sql("SELECT * FROM calendar_event_evidence WHERE event_id=:id ORDER BY fetched_at DESC")
                .param("id", eventId.toString()).query(this::evidence).list();
    }

    @Override public List<CalendarEventRevision> listRevisions(UUID eventId) {
        return jdbc.sql("SELECT * FROM calendar_event_revisions WHERE event_id=:id ORDER BY changed_at DESC")
                .param("id", eventId.toString()).query(this::revision).list();
    }

    @Override public void updateSourceStatus(UUID sourceId, CalendarSourceHealth health, Instant attemptedAt, Instant successAt,
                                             Instant nextAllowedAt, String warning, int httpStatus, int parsed, int inserted, int updated) {
        jdbc.sql("""
                UPDATE calendar_sources SET health=:health,last_attempt_at=:attempt,last_success_at=COALESCE(:success,last_success_at),
                next_allowed_at=:next,warning=:warning,last_http_status=:http,last_parsed_count=:parsed,last_inserted_count=:inserted,
                last_updated_count=:updated,updated_at=:attempt WHERE id=:id
                """).params(Map.ofEntries(entry("health", health), entry("attempt", attemptedAt), entry("success", successAt), entry("next", nextAllowedAt),
                entry("warning", warning == null ? "" : warning), entry("http", httpStatus), entry("parsed", parsed), entry("inserted", inserted),
                entry("updated", updated), entry("id", sourceId))).update();
    }

    @Override public long countEvents() {
        return jdbc.sql("SELECT COUNT(*) FROM calendar_events").query(Long.class).single();
    }

    private CalendarSource source(ResultSet r, int row) throws SQLException {
        return new CalendarSource(uuid(r.getString("id")), r.getString("source_key"), r.getString("name"),
                CalendarSourceType.valueOf(r.getString("type")), CalendarSourceTier.valueOf(r.getString("tier")), r.getString("endpoint"),
                r.getString("schedule"), r.getBoolean("enabled"), r.getLong("min_interval_seconds"), r.getInt("daily_budget"),
                instant(r.getString("next_allowed_at")), instant(r.getString("last_attempt_at")), instant(r.getString("last_success_at")),
                CalendarSourceHealth.valueOf(r.getString("health")), r.getString("parser_version"), r.getString("warning"),
                r.getInt("last_http_status"), r.getInt("last_parsed_count"), r.getInt("last_inserted_count"), r.getInt("last_updated_count"),
                read(r.getString("countries_json"), STRING_SET), read(r.getString("categories_json"), STRING_SET),
                instant(r.getString("created_at")), instant(r.getString("updated_at")));
    }

    private RawCalendarSnapshot snapshot(ResultSet r, int row) throws SQLException {
        return new RawCalendarSnapshot(uuid(r.getString("id")), uuid(r.getString("source_id")), r.getString("source_key"), r.getString("url"),
                r.getString("final_url"), r.getInt("http_status"), r.getString("content_type"), r.getString("etag"), r.getString("last_modified"),
                instant(r.getString("fetched_at")), r.getString("content_hash"), r.getLong("content_length"), r.getString("parser_version"), r.getString("body"));
    }

    private CalendarEvent event(ResultSet r, int row) throws SQLException {
        return new CalendarEvent(uuid(r.getString("id")), r.getString("event_key"), r.getString("indicator_code"), r.getString("name_zh"),
                r.getString("name_original"), r.getString("country_code"), r.getString("region"), r.getString("currency"),
                CalendarCategory.valueOf(r.getString("category")), CalendarImportance.valueOf(r.getString("importance")),
                r.getString("importance_source"), instant(r.getString("scheduled_at_utc")), r.getString("source_timezone"),
                r.getString("scheduled_local_text"), r.getString("period"), r.getString("actual"), r.getString("forecast"),
                r.getString("previous"), r.getString("revised_previous"), r.getString("unit"), CalendarEventStatus.valueOf(r.getString("status")),
                r.getString("official_url"), uuid(r.getString("primary_source_id")), instant(r.getString("first_seen_at")),
                instant(r.getString("last_seen_at")), instant(r.getString("released_at")), r.getInt("version"));
    }

    private CalendarEventEvidence evidence(ResultSet r, int row) throws SQLException {
        return new CalendarEventEvidence(uuid(r.getString("id")), uuid(r.getString("event_id")), uuid(r.getString("source_id")),
                uuid(r.getString("raw_snapshot_id")), r.getString("source_event_id"), r.getString("source_url"), instant(r.getString("fetched_at")),
                r.getString("parser_version"), r.getString("raw_fields_json"), r.getString("warnings_json"),
                CalendarSourceTier.valueOf(r.getString("source_tier")));
    }

    private CalendarEventRevision revision(ResultSet r, int row) throws SQLException {
        return new CalendarEventRevision(uuid(r.getString("id")), uuid(r.getString("event_id")), instant(r.getString("changed_at")),
                uuid(r.getString("source_id")), r.getString("changed_fields_json"), r.getString("reason"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("calendar json serialization failed", ex);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("calendar json deserialization failed", ex);
        }
    }

    private static Instant instant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static Map.Entry<String, Object> entry(String key, Object value) {
        Object converted = value == null ? new SqlParameterValue(Types.VARCHAR, null)
                : value instanceof UUID u ? u.toString()
                : value instanceof Enum<?> e ? e.name()
                : value instanceof Instant i ? i.toString()
                : value;
        return new AbstractMap.SimpleEntry<>(key, converted);
    }
}
