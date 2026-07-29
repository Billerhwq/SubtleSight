package com.subtlesight.storage.sqlite;

import com.subtlesight.application.AssistantPorts.Repository;
import com.subtlesight.domain.AssistantModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteAssistantRepository implements Repository {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SqliteAssistantRepository(DataSource dataSource, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clock;
    }

    // ── Row mappers ──

    private static final RowMapper<AssistantSession> SESSION_MAPPER = (rs, n) -> new AssistantSession(
            UUID.fromString(rs.getString("id")), rs.getString("title"),
            Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")),
            rs.getInt("archived") == 1);

    private static final RowMapper<AssistantTurn> TURN_MAPPER = (rs, n) -> new AssistantTurn(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("session_id")),
            Role.valueOf(rs.getString("role")), rs.getString("content"),
            TurnStatus.valueOf(rs.getString("status")),
            rs.getString("plan_json"), rs.getString("tools_json"), rs.getString("context_json"),
            Instant.parse(rs.getString("created_at")),
            nullableInstant(rs.getString("completed_at")));

    // ── Sessions ──

    @Override
    public AssistantSession createSession(String title) {
        Instant now = clock.instant();
        AssistantSession session = new AssistantSession(
                UUID.randomUUID(), title, now, now, false);
        jdbc.update("INSERT INTO assistant_sessions(id,title,created_at,updated_at) VALUES(?,?,?,?)",
                session.id().toString(), session.title(),
                session.createdAt().toString(), session.updatedAt().toString());
        return session;
    }

    @Override
    public Optional<AssistantSession> getSession(UUID sessionId) {
        return jdbc.query("SELECT * FROM assistant_sessions WHERE id=?",
                SESSION_MAPPER, sessionId.toString()).stream().findFirst();
    }

    @Override
    public List<AssistantSession> listSessions(int limit, int offset) {
        return jdbc.query("SELECT * FROM assistant_sessions WHERE archived=0 ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                SESSION_MAPPER, Math.max(1, Math.min(limit, 100)), Math.max(0, offset));
    }

    @Override
    public void updateSessionTitle(UUID sessionId, String title) {
        jdbc.update("UPDATE assistant_sessions SET title=?,updated_at=? WHERE id=?",
                title, clock.instant().toString(), sessionId.toString());
    }

    @Override
    public void archiveSession(UUID sessionId) {
        jdbc.update("UPDATE assistant_sessions SET archived=1,updated_at=? WHERE id=?",
                clock.instant().toString(), sessionId.toString());
    }

    // ── Turns ──

    @Override
    public UUID appendTurn(UUID sessionId, Role role, String content, String contextJson) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO assistant_turns(id,session_id,role,content,status,context_json,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, id.toString(), sessionId.toString(), role.name(), content,
                TurnStatus.PENDING.name(), contextJson, now.toString());
        // Bump session updated_at
        jdbc.update("UPDATE assistant_sessions SET updated_at=? WHERE id=?", now.toString(), sessionId.toString());
        return id;
    }

    @Override
    public Optional<AssistantTurn> getTurn(UUID turnId) {
        return jdbc.query("SELECT * FROM assistant_turns WHERE id=?",
                TURN_MAPPER, turnId.toString()).stream().findFirst();
    }

    @Override
    public void updateTurnStatus(UUID turnId, TurnStatus status, String planJson, String toolsJson) {
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE assistant_turns SET status=?,plan_json=?,tools_json=?,completed_at=?
                WHERE id=?
                """, status.name(), planJson, toolsJson,
                status == TurnStatus.COMPLETED || status == TurnStatus.FAILED || status == TurnStatus.CANCELLED
                        ? now.toString() : null,
                turnId.toString());
    }

    @Override
    public List<AssistantTurn> listTurns(UUID sessionId, int limit) {
        return jdbc.query("SELECT * FROM assistant_turns WHERE session_id=? ORDER BY created_at ASC LIMIT ?",
                TURN_MAPPER, sessionId.toString(), Math.max(1, Math.min(limit, 500)));
    }

    // ── Audit ──

    @Override
    public void appendAudit(UUID turnId, String action, String detailJson) {
        jdbc.update("INSERT INTO assistant_audit(id,turn_id,action,detail_json,created_at) VALUES(?,?,?,?,?)",
                UUID.randomUUID().toString(), turnId.toString(), action, detailJson,
                clock.instant().toString());
    }

    // ── Helpers ──

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
