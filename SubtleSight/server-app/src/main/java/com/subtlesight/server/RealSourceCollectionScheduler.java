package com.subtlesight.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class RealSourceCollectionScheduler {
    private final SubtleSightWorkflowService workflow;
    private final boolean enabled;
    private final int maxSources;
    private final int maxItems;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RealSourceCollectionScheduler(
            SubtleSightWorkflowService workflow,
            @Value("${subtlesight.collect.scheduler.enabled:true}") boolean enabled,
            @Value("${subtlesight.collect.scheduler.max-sources:7}") int maxSources,
            @Value("${subtlesight.collect.scheduler.max-items:3}") int maxItems) {
        this.workflow = workflow;
        this.enabled = enabled;
        this.maxSources = Math.max(1, maxSources);
        this.maxItems = Math.max(1, maxItems);
    }

    @Scheduled(
            initialDelayString = "${subtlesight.collect.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${subtlesight.collect.scheduler.fixed-delay-ms:1800000}")
    public void collectRealSources() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            workflow.collectRealSources(maxSources, maxItems);
        } finally {
            running.set(false);
        }
    }
}
