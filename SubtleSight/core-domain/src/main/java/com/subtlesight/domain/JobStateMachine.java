package com.subtlesight.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static com.subtlesight.domain.Models.JobStatus;

public final class JobStateMachine {
    private static final Map<JobStatus, EnumSet<JobStatus>> ALLOWED = new EnumMap<>(JobStatus.class);
    static {
        ALLOWED.put(JobStatus.QUEUED, EnumSet.of(JobStatus.RUNNING, JobStatus.PAUSED, JobStatus.CANCELLED));
        ALLOWED.put(JobStatus.RUNNING, EnumSet.of(JobStatus.COMPLETED, JobStatus.RETRY_WAIT, JobStatus.FAILED, JobStatus.CANCELLED));
        ALLOWED.put(JobStatus.RETRY_WAIT, EnumSet.of(JobStatus.QUEUED, JobStatus.CANCELLED, JobStatus.FAILED));
        ALLOWED.put(JobStatus.PAUSED, EnumSet.of(JobStatus.QUEUED, JobStatus.CANCELLED));
        ALLOWED.put(JobStatus.COMPLETED, EnumSet.noneOf(JobStatus.class));
        ALLOWED.put(JobStatus.FAILED, EnumSet.noneOf(JobStatus.class));
        ALLOWED.put(JobStatus.CANCELLED, EnumSet.noneOf(JobStatus.class));
    }
    private JobStateMachine() {}
    public static void requireTransition(JobStatus from, JobStatus to) {
        if (from == null || to == null || !ALLOWED.getOrDefault(from, EnumSet.noneOf(JobStatus.class)).contains(to))
            throw new IllegalStateException("illegal job transition: " + from + " -> " + to);
    }
}

