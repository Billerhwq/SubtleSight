package com.subtlesight.jobs;

import java.time.Duration;
import java.util.Set;
import java.util.SplittableRandom;

public final class RetryPolicy {
    private static final Set<String> NEVER_RETRY = Set.of("AUTH_401", "AUTH_403", "VALIDATION", "CANCELLED", "NOT_FOUND");
    private final Duration base;
    private final Duration maximum;
    private final SplittableRandom random;
    public RetryPolicy(Duration base, Duration maximum, long seed) {
        this.base = base; this.maximum = maximum; this.random = new SplittableRandom(seed);
    }
    public boolean retryable(String errorCode, int attempt, int maxAttempts) {
        return attempt < maxAttempts && !NEVER_RETRY.contains(errorCode == null ? "" : errorCode);
    }
    public synchronized Duration delay(int attempt, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) return retryAfter.compareTo(maximum) > 0 ? maximum : retryAfter;
        long exponential = Math.min(maximum.toMillis(), base.toMillis() * (1L << Math.min(Math.max(attempt - 1, 0), 20)));
        double jitter = 0.8 + random.nextDouble() * 0.4;
        return Duration.ofMillis(Math.max(1, (long) (exponential * jitter)));
    }
}

