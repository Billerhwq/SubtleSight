package com.subtlesight.jobs;

import com.subtlesight.domain.JobStateMachine;
import com.subtlesight.domain.Models.Job;
import com.subtlesight.domain.Models.JobStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DurableJobQueue {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration leaseDuration;
    private final RetryPolicy retryPolicy;

    public DurableJobQueue(DataSource dataSource, Clock clock, Duration leaseDuration, RetryPolicy retryPolicy) {
        this.jdbc = JdbcClient.create(dataSource); this.clock = clock; this.leaseDuration = leaseDuration; this.retryPolicy = retryPolicy;
    }

    public Job submit(String type, int priority, String payloadJson, String dedupKey, int maxAttempts, Instant runAfter) {
        Instant now = clock.instant();
        Job job = new Job(UUID.randomUUID(), type, JobStatus.QUEUED, priority, payloadJson, dedupKey, 0, maxAttempts,
                runAfter == null ? now : runAfter, null, null, null, false, null, now, null, null);
        try {
            jdbc.sql("""
                    INSERT INTO jobs(id,type,status,priority,payload_json,dedup_key,attempt,max_attempts,run_after,cancel_requested,created_at)
                    VALUES(:id,:type,:status,:priority,:payload,:dedup,:attempt,:max,:runAfter,0,:created)
                    """).param("id", job.id().toString()).param("type", type).param("status", job.status().name())
                    .param("priority", priority).param("payload", payloadJson).param("dedup", dedupKey).param("attempt", 0)
                    .param("max", maxAttempts).param("runAfter", job.runAfter().toString()).param("created", now.toString()).update();
            return job;
        } catch (DataAccessException duplicate) {
            return findActiveByDedupKey(dedupKey).orElseThrow(() -> duplicate);
        }
    }

    public Optional<Job> claim() {
        Instant now = clock.instant();
        Instant lease = now.plus(leaseDuration);
        return jdbc.sql("""
                UPDATE jobs SET status='RUNNING', attempt=attempt+1, started_at=COALESCE(started_at,:now),
                  lease_until=:lease, heartbeat_at=:now
                WHERE id=(SELECT id FROM jobs WHERE status IN ('QUEUED','RETRY_WAIT') AND run_after<=:now
                  AND (lease_until IS NULL OR lease_until<:now) AND cancel_requested=0
                  ORDER BY priority DESC,created_at LIMIT 1)
                RETURNING *
                """).param("now", now.toString()).param("lease", lease.toString()).query(this::map).optional();
    }

    public void heartbeat(UUID id) {
        Instant now = clock.instant();
        int changed = jdbc.sql("UPDATE jobs SET heartbeat_at=:now,lease_until=:lease WHERE id=:id AND status='RUNNING' AND cancel_requested=0")
                .param("now", now.toString()).param("lease", now.plus(leaseDuration).toString()).param("id", id.toString()).update();
        if (changed == 0) throw new IllegalStateException("job lease lost or cancellation requested");
    }
    public void checkpoint(UUID id, String checkpointJson) {
        jdbc.sql("UPDATE jobs SET checkpoint_json=:checkpoint WHERE id=:id AND status='RUNNING'")
                .param("checkpoint", checkpointJson).param("id", id.toString()).update();
    }
    public void complete(UUID id) { transition(id, JobStatus.RUNNING, JobStatus.COMPLETED, null, clock.instant()); }
    public void cancel(UUID id) {
        jdbc.sql("UPDATE jobs SET cancel_requested=1,status=CASE WHEN status IN ('QUEUED','RETRY_WAIT','PAUSED') THEN 'CANCELLED' ELSE status END,finished_at=CASE WHEN status IN ('QUEUED','RETRY_WAIT','PAUSED') THEN :now ELSE finished_at END WHERE id=:id")
                .param("now", clock.instant().toString()).param("id", id.toString()).update();
    }
    public boolean cancellationRequested(UUID id) {
        return jdbc.sql("SELECT cancel_requested FROM jobs WHERE id=:id").param("id", id.toString()).query(Boolean.class).optional().orElse(true);
    }
    public Job fail(UUID id, String errorCode, Duration retryAfter) {
        Job current = find(id).orElseThrow();
        if (current.cancelRequested()) { transition(id, JobStatus.RUNNING, JobStatus.CANCELLED, "CANCELLED", clock.instant()); return find(id).orElseThrow(); }
        if (retryPolicy.retryable(errorCode, current.attempt(), current.maxAttempts())) {
            JobStateMachine.requireTransition(JobStatus.RUNNING, JobStatus.RETRY_WAIT);
            Instant retryAt = clock.instant().plus(retryPolicy.delay(current.attempt(), retryAfter));
            jdbc.sql("UPDATE jobs SET status='RETRY_WAIT',run_after=:runAfter,lease_until=NULL,heartbeat_at=NULL,error_code=:error WHERE id=:id AND status='RUNNING'")
                    .param("runAfter", retryAt.toString()).param("error", errorCode).param("id", id.toString()).update();
        } else transition(id, JobStatus.RUNNING, JobStatus.FAILED, errorCode, clock.instant());
        return find(id).orElseThrow();
    }
    public int recoverExpiredLeases() {
        Instant now = clock.instant();
        return jdbc.sql("""
                UPDATE jobs SET status=CASE WHEN cancel_requested=1 THEN 'CANCELLED' ELSE 'RETRY_WAIT' END,
                  run_after=:now,lease_until=NULL,heartbeat_at=NULL,error_code='LEASE_EXPIRED',
                  finished_at=CASE WHEN cancel_requested=1 THEN :now ELSE finished_at END
                WHERE status='RUNNING' AND lease_until<:now
                """).param("now", now.toString()).update();
    }
    public Optional<Job> find(UUID id) { return jdbc.sql("SELECT * FROM jobs WHERE id=:id").param("id", id.toString()).query(this::map).optional(); }
    public Optional<Job> findActiveByDedupKey(String key) {
        return jdbc.sql("SELECT * FROM jobs WHERE dedup_key=:key AND status IN ('QUEUED','RUNNING','RETRY_WAIT','PAUSED') ORDER BY created_at LIMIT 1")
                .param("key", key).query(this::map).optional();
    }
    public List<Job> list(int limit) { return jdbc.sql("SELECT * FROM jobs ORDER BY created_at DESC LIMIT :limit").param("limit", limit).query(this::map).list(); }

    private void transition(UUID id, JobStatus from, JobStatus to, String error, Instant now) {
        JobStateMachine.requireTransition(from, to);
        int changed = jdbc.sql("UPDATE jobs SET status=:to,lease_until=NULL,heartbeat_at=NULL,error_code=:error,finished_at=:finished WHERE id=:id AND status=:from")
                .param("to", to.name()).param("error", error).param("finished", now.toString()).param("id", id.toString()).param("from", from.name()).update();
        if (changed != 1) throw new IllegalStateException("concurrent job transition rejected");
    }
    private Job map(ResultSet r, int row) throws SQLException {
        return new Job(UUID.fromString(r.getString("id")), r.getString("type"), JobStatus.valueOf(r.getString("status")), r.getInt("priority"),
                r.getString("payload_json"), r.getString("dedup_key"), r.getInt("attempt"), r.getInt("max_attempts"), instant(r,"run_after"),
                instant(r,"lease_until"), instant(r,"heartbeat_at"), r.getString("checkpoint_json"), r.getBoolean("cancel_requested"),
                r.getString("error_code"), instant(r,"created_at"), instant(r,"started_at"), instant(r,"finished_at"));
    }
    private static Instant instant(ResultSet r, String name) throws SQLException { String value=r.getString(name); return value==null?null:Instant.parse(value); }
}

