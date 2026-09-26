package com.infinevo.core.holiday;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tenant holiday calendars and holiday querying (W-17).
 */
@RestController
public class HolidayCalendarController {

    private final HolidayCalendarService calendarService;
    private final HolidayQueryService queryService;

    public HolidayCalendarController(HolidayCalendarService calendarService, HolidayQueryService queryService) {
        this.calendarService = Objects.requireNonNull(calendarService, "calendarService must not be null");
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @PostMapping("/api/v1/holiday-calendars")
    @RequiresAction("core.holiday.manage")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayCalendarResponse createCalendar(@RequestBody HolidayCalendarRequest request) {
        return calendarService.createCalendar(request);
    }

    @GetMapping("/api/v1/holiday-calendars")
    @RequiresAction("core.holiday.read")
    public List<HolidayCalendarResponse> listCalendars() {
        return calendarService.listCalendars();
    }

    @GetMapping("/api/v1/holiday-calendars/{id}")
    @RequiresAction("core.holiday.read")
    public HolidayCalendarResponse getCalendar(@PathVariable("id") UUID id) {
        return calendarService.getCalendar(id);
    }

    @PutMapping("/api/v1/holiday-calendars/{id}")
    @RequiresAction("core.holiday.manage")
    public HolidayCalendarResponse updateCalendar(
            @PathVariable("id") UUID id, @RequestBody HolidayCalendarRequest request) {
        return calendarService.updateCalendar(id, request);
    }

    @PostMapping("/api/v1/holiday-calendars/{id}/holidays")
    @RequiresAction("core.holiday.manage")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse addHoliday(@PathVariable("id") UUID id, @RequestBody HolidayRequest request) {
        return calendarService.addHoliday(id, request);
    }

    @DeleteMapping("/api/v1/holiday-calendars/{id}/holidays/{holidayId}")
    @RequiresAction("core.holiday.manage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable("id") UUID id, @PathVariable("holidayId") UUID holidayId) {
        calendarService.deleteHoliday(id, holidayId);
    }

    @GetMapping("/api/v1/holidays")
    @RequiresAction("core.holiday.read")
    public List<HolidayResponse> getHolidays(
            @RequestParam(name = "workLocationId", required = false) UUID workLocationId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate toDate = to != null ? to : fromDate.plusYears(1).minusDays(1);
        return queryService.holidaysBetween(workLocationId, fromDate, toDate);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
