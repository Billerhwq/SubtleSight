package com.subtlesight.server;

import com.subtlesight.calendar.CalendarModels.CalendarCategory;
import com.subtlesight.calendar.CalendarModels.CalendarEventStatus;
import com.subtlesight.calendar.CalendarModels.CalendarImportance;
import com.subtlesight.calendar.CalendarModels.CalendarQuery;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CalendarIcsAliasController {
    private final FinancialCalendarService calendar;

    public CalendarIcsAliasController(FinancialCalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping(value = "/api/v1/calendar.ics", produces = "text/calendar")
    ResponseEntity<String> ics(@RequestParam(required = false) Instant from,
                               @RequestParam(required = false) Instant to,
                               @RequestParam(required = false) String countries,
                               @RequestParam(required = false) String categories,
                               @RequestParam(required = false) String importance,
                               @RequestParam(required = false) String status) {
        String body = calendar.ics(new CalendarQuery(from, to, split(countries), enums(categories, CalendarCategory.class),
                enums(importance, CalendarImportance.class), enums(status, CalendarEventStatus.class), 500));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar; charset=utf-8")).body(body);
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(String::toUpperCase).collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> Set<E> enums(String value, Class<E> type) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(String::toUpperCase)
                .map(v -> Enum.valueOf(type, v)).collect(Collectors.toSet());
    }
}
