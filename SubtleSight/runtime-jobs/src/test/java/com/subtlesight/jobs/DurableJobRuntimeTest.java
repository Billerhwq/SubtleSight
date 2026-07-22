package com.subtlesight.jobs;

import com.subtlesight.domain.Models.JobStatus;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class DurableJobRuntimeTest {
 @TempDir Path temp;
 @Test void queueCoversRetryCancelHeartbeatAndTerminalGuards(){var q=queue();var a=q.submit("A",1,"{}","a",2,null);assertThat(q.submit("A",1,"{}","a",2,null).id()).isEqualTo(a.id());var running=q.claim().orElseThrow();q.heartbeat(running.id());q.checkpoint(running.id(),"{\"p\":1}");assertThat(q.fail(running.id(),"HTTP_503",Duration.ofMillis(1)).status()).isEqualTo(JobStatus.RETRY_WAIT);sleep();var again=q.claim().orElseThrow();q.cancel(again.id());assertThat(q.cancellationRequested(again.id())).isTrue();assertThat(q.fail(again.id(),"X",null).status()).isEqualTo(JobStatus.CANCELLED);var b=q.submit("B",1,"{}","b",1,null);q.cancel(b.id());assertThat(q.find(b.id()).orElseThrow().status()).isEqualTo(JobStatus.CANCELLED);assertThat(q.cancellationRequested(java.util.UUID.randomUUID())).isTrue();assertThatThrownBy(()->q.heartbeat(java.util.UUID.randomUUID())).isInstanceOf(IllegalStateException.class);assertThat(q.list(20)).hasSize(2);}
 @Test void runnerHandlesSuccessRetryMissingHandlerAndRegistrationGuards()throws Exception{var q=queue();try(var runner=new DurableJobRunner(q)){assertThatThrownBy(()->runner.register("bad",0,(j,c)->{})).isInstanceOf(IllegalArgumentException.class);CountDownLatch done=new CountDownLatch(1);runner.register("OK",1,(j,c)->{c.checkpoint("{}");c.heartbeat();done.countDown();});assertThatThrownBy(()->runner.register("OK",1,(j,c)->{})).isInstanceOf(IllegalStateException.class);var ok=q.submit("OK",1,"{}","ok",2,null);assertThat(runner.tick()).isTrue();assertThat(done.await(5,TimeUnit.SECONDS)).isTrue();awaitStatus(q,ok.id(),JobStatus.COMPLETED);q.submit("NONE",1,"{}","none",1,null);assertThat(runner.tick()).isTrue();awaitStatus(q,q.list(10).stream().filter(j->j.type().equals("NONE")).findFirst().orElseThrow().id(),JobStatus.FAILED);assertThat(runner.tick()).isFalse();}}
 @Test void schedulesRespectMissedRunContract(){Instant now=Instant.parse("2026-07-16T00:00:00Z");assertThat(ScheduleSupport.next("0 0 * * * *",now,ZoneId.of("UTC"))).isAfter(now);assertThat(ScheduleSupport.shouldRunMissedOnce(now.minusSeconds(1),now,null)).isTrue();assertThat(ScheduleSupport.shouldRunMissedOnce(null,now,null)).isFalse();assertThat(new RetryPolicy(Duration.ofSeconds(1),Duration.ofSeconds(2),1).delay(1,Duration.ofSeconds(9))).isEqualTo(Duration.ofSeconds(2));}
 private DurableJobQueue queue(){return new DurableJobQueue(SqliteDataSourceFactory.create(temp.resolve(java.util.UUID.randomUUID()+".db")),Clock.systemUTC(),Duration.ofSeconds(2),new RetryPolicy(Duration.ofMillis(1),Duration.ofSeconds(1),2));}
 private void sleep(){try{Thread.sleep(5);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 private void awaitStatus(DurableJobQueue q,java.util.UUID id,JobStatus s)throws Exception{for(int i=0;i<100;i++){if(q.find(id).orElseThrow().status()==s)return;Thread.sleep(20);}fail("status not reached "+s);}
}
