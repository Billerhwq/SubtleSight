package com.subtlesight.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class FinancialCalendarScheduler {
    private final FinancialCalendarService calendar;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FinancialCalendarScheduler(FinancialCalendarService calendar,
                                      @Value("${subtlesight.calendar.scheduler.enabled:true}") boolean enabled) {
        this.calendar = calendar;
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "${subtlesight.calendar.scheduler.initial-delay-ms:90000}",
            fixedDelayString = "${subtlesight.calendar.scheduler.fixed-delay-ms:1800000}")
    public void refreshCalendar() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            calendar.refresh("scheduled");
        } finally {
            running.set(false);
        }
    }
}
