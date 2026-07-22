package com.subtlesight.acceptance;

import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.jobs.RetryPolicy;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CrashRecoveryAcceptanceTest {
    @TempDir Path temp;
    @Test void e2e06ExpiredLeaseRecoversWithoutCreatingDuplicateExecution(){MutableClock clock=new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));var ds=SqliteDataSourceFactory.create(temp.resolve("recovery.db"));var queue=new DurableJobQueue(ds,clock,Duration.ofSeconds(10),new RetryPolicy(Duration.ofSeconds(1),Duration.ofMinutes(1),7));var submitted=queue.submit("FETCH",10,"{}","fetch:one",3,null);assertThat(queue.submit("FETCH",10,"{}","fetch:one",3,null).id()).isEqualTo(submitted.id());var claimed=queue.claim().orElseThrow();assertThat(claimed.attempt()).isEqualTo(1);clock.advance(Duration.ofSeconds(11));assertThat(queue.recoverExpiredLeases()).isEqualTo(1);var resumed=queue.claim().orElseThrow();assertThat(resumed.id()).isEqualTo(submitted.id());assertThat(resumed.attempt()).isEqualTo(2);queue.checkpoint(resumed.id(),"{\"page\":3}");queue.complete(resumed.id());assertThat(queue.find(resumed.id()).orElseThrow().status().name()).isEqualTo("COMPLETED");assertThat(queue.claim()).isEmpty();}
    static final class MutableClock extends Clock{private Instant now;MutableClock(Instant now){this.now=now;}void advance(Duration d){now=now.plus(d);}@Override public ZoneId getZone(){return ZoneId.of("UTC");}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return now;}}
}
