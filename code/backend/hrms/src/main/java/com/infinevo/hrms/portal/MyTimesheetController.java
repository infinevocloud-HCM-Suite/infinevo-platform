package com.infinevo.hrms.portal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.hrms.timesheet.TimesheetResponse;
import com.infinevo.hrms.timesheet.TimesheetService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's timesheet for a week, for the self-service portal (W-42.1 §4). It replaces
 * {@code MyTimesheetPlaceholderController} on the same path, {@code /api/v1/me/timesheet}, which answered
 * {@code "status": "placeholder"}; {@link TimesheetPanelProvider} is unchanged.
 *
 * <p>Guarded by {@code @RequiresModule(HRMS)} and {@code hrms.timesheet.read_own}. A login with no employee record
 * is the permission error (403), not the 500 that W-25 gave on {@code /me/employee} for the same case.
 */
@RestController
@RequestMapping("/api/v1/me/timesheet")
@RequiresModule(PlatformModule.HRMS)
public class MyTimesheetController {

    private final TimesheetService timesheetService;

    public MyTimesheetController(TimesheetService timesheetService) {
        this.timesheetService = Objects.requireNonNull(timesheetService, "timesheetService must not be null");
    }

    /**
     * The week's timesheet, or {@code data: null} when there is none. The null is written out: a portal panel tells
     * "nothing this week" from a malformed answer by the field being there.
     *
     * @param weekStart the Monday of the week, {@code yyyy-MM-dd}; this week's when absent
     */
    @GetMapping
    @RequiresAction("hrms.timesheet.read_own")
    public MyTimesheetResponse getMyTimesheet(
            @RequestParam(value = "weekStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate weekStart) {
        return new MyTimesheetResponse(
                "success",
                "Operation successful",
                timesheetService.forWeek(weekStart).orElse(null));
    }

    /** The standard envelope, but with {@code data} present even when it is null. */
    public record MyTimesheetResponse(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message,
            @JsonProperty("data") @JsonInclude(JsonInclude.Include.ALWAYS) TimesheetResponse data) {}
}
