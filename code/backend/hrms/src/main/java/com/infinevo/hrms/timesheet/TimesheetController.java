package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own timesheets (W-42.1 §4). Thin by rule: unpack, delegate, repack
 * ({@code docs/CONVENTIONS.md} section 3). Errors are mapped by {@link TimesheetErrors}.
 *
 * <p>Writing needs {@code hrms.timesheet.submit} and reading {@code hrms.timesheet.read_own}. Neither lets a caller
 * reach another employee's timesheet: the service finds a timesheet only by its owner, so someone else's id is a 404.
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;

    public TimesheetController(TimesheetService timesheetService) {
        this.timesheetService = Objects.requireNonNull(timesheetService, "timesheetService must not be null");
    }

    @PostMapping
    @RequiresAction(TimesheetServiceImpl.ACTION_SUBMIT)
    public ResponseEntity<ApiResponse<TimesheetResponse>> create(@RequestBody TimesheetRequest request) {
        TimesheetResponse created = timesheetService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/hrms/timesheets/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @RequiresAction(TimesheetServiceImpl.ACTION_SUBMIT)
    public ApiResponse<TimesheetResponse> replace(@PathVariable("id") UUID id, @RequestBody TimesheetRequest request) {
        return ApiResponse.success(timesheetService.replace(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresAction(TimesheetServiceImpl.ACTION_SUBMIT)
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        timesheetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mine")
    @RequiresAction(TimesheetServiceImpl.ACTION_READ_OWN)
    public ApiResponse<List<TimesheetResponse>> listMine(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) TimesheetStatus status,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ApiResponse.success(timesheetService.listMine(from, to, status, projectId));
    }

    @GetMapping("/{id}")
    @RequiresAction(TimesheetServiceImpl.ACTION_READ_OWN)
    public ApiResponse<TimesheetResponse> get(@PathVariable("id") UUID id) {
        return ApiResponse.success(timesheetService.get(id));
    }
}
