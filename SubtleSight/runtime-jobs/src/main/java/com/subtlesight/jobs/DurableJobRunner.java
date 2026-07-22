package com.subtlesight.jobs;

import com.subtlesight.domain.Models.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class DurableJobRunner implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DurableJobRunner.class);
    private final DurableJobQueue queue;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> limits = new ConcurrentHashMap<>();
    public DurableJobRunner(DurableJobQueue queue) { this.queue = queue; }
    public void register(String type, int concurrency, JobHandler handler) {
        if (concurrency < 1) throw new IllegalArgumentException("concurrency must be positive");
        if (handlers.putIfAbsent(type, handler) != null) throw new IllegalStateException("duplicate job handler " + type);
        limits.put(type, new Semaphore(concurrency));
    }
    /** Claims at most one job. Scheduling this frequently keeps user jobs responsive without busy spinning. */
    public boolean tick() {
        var claimed = queue.claim();
        if (claimed.isEmpty()) return false;
        Job job = claimed.get();
        JobHandler handler = handlers.get(job.type());
        if (handler == null) { queue.fail(job.id(), "NO_HANDLER", null); return true; }
        Semaphore limit = limits.get(job.type());
        if (!limit.tryAcquire()) { queue.fail(job.id(), "CONCURRENCY_LIMIT", Duration.ofSeconds(1)); return true; }
        executor.submit(() -> {
            try {
                handler.handle(job, new Context(queue, job));
                if (queue.cancellationRequested(job.id())) queue.fail(job.id(), "CANCELLED", null);
                else queue.complete(job.id());
            } catch (RetryableJobException ex) { queue.fail(job.id(), ex.code(), ex.retryAfter()); }
            catch (Exception ex) { log.error("job {} failed", job.id(), ex); queue.fail(job.id(), "UNEXPECTED", null); }
            finally { limit.release(); }
        });
        return true;
    }
    @Override public void close() { executor.close(); }
    @FunctionalInterface public interface JobHandler { void handle(Job job, Context context) throws Exception; }
    public record Context(DurableJobQueue queue, Job job) {
        public void heartbeat() { queue.heartbeat(job.id()); }
        public void checkpoint(String json) { queue.checkpoint(job.id(), json); }
        public void checkCancelled() { if (queue.cancellationRequested(job.id())) throw new IllegalStateException("job cancelled"); }
    }
    public static final class RetryableJobException extends RuntimeException {
        private final String code; private final Duration retryAfter;
        public RetryableJobException(String code, Duration retryAfter, Throwable cause) { super(code, cause); this.code=code; this.retryAfter=retryAfter; }
        public String code() { return code; } public Duration retryAfter() { return retryAfter; }
    }
}

