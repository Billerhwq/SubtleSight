package com.subtlesight.jobs;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ScheduleSupport {
    private ScheduleSupport() {}
    public static Instant next(String cron, Instant after, ZoneId zone) {
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.ofInstant(after, zone));
        if (next == null) throw new IllegalArgumentException("schedule has no next occurrence");
        return next.toInstant();
    }
    public static boolean shouldRunMissedOnce(Instant previousNextRun, Instant now, Instant lastRun) {
        return previousNextRun != null && previousNextRun.isBefore(now) && (lastRun == null || lastRun.isBefore(previousNextRun));
    }
}

