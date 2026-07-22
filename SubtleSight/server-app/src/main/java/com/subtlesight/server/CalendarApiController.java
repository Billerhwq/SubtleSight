package com.subtlesight.server;

import com.subtlesight.calendar.CalendarModels.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarApiController {
    private final FinancialCalendarService calendar;
    private final SseHub sse;

    public CalendarApiController(FinancialCalendarService calendar, SseHub sse) {
        this.calendar = calendar;
        this.sse = sse;
    }

    @GetMapping("/events") java.util.List<CalendarEvent> events(@RequestParam(required = false) Instant from,
                                                                  @RequestParam(required = false) Instant to,
                                                                  @RequestParam(required = false) String countries,
                                                                  @RequestParam(required = false) String categories,
                                                                  @RequestParam(required = false) String importance,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(defaultValue = "200") int limit) {
        return calendar.events(new CalendarQuery(from, to, split(countries), enums(categories, CalendarCategory.class),
                enums(importance, CalendarImportance.class), enums(status, CalendarEventStatus.class), Math.min(limit, 500)));
    }

    @GetMapping("/events/{id}") CalendarEventDetail event(@PathVariable UUID id) {
        return calendar.detail(id);
    }

    @GetMapping("/sources") java.util.List<CalendarSource> sources() {
        return calendar.sources();
    }

    @PostMapping("/refresh") CalendarRefreshReport refresh(@RequestParam(defaultValue = "today") String scope, CsrfToken token) {
        token.getToken();
        CalendarRefreshReport report = calendar.refresh(scope);
        sse.publish("calendar-refresh", report);
        return report;
    }

    @PostMapping("/sources/{id}/refresh") CalendarRefreshReport refreshSource(@PathVariable UUID id, CsrfToken token) {
        token.getToken();
        CalendarRefreshReport report = calendar.refreshSource(id);
        sse.publish("calendar-refresh", report);
        return report;
    }

    @GetMapping(value = "/calendar.ics", produces = "text/calendar")
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

    @GetMapping("/stream") SseEmitter stream() {
        return sse.subscribe();
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
