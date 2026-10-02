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
 * The caller's own timesheets (W-42.1 §4), their submit and resubmit (W-42.3 §4), the project line an approver is asked
 * to decide (W-42.3), and the review lists over everyone's (W-42.4 §4). Thin by rule: unpack,
 * delegate, repack ({@code docs/CONVENTIONS.md} section 3). Errors are mapped by {@link TimesheetErrors}.
 *
 * <p>Writing needs {@code hrms.timesheet.submit} and reading one's own {@code hrms.timesheet.read_own}. Neither lets a
 * caller write to another employee's timesheet: the service finds a timesheet only by its owner, so someone else's id
 * is a 404. The three review lists are read-only, each behind its own action; what a caller sees of a week, and of
 * {@code GET /{id}}, is {@link TimesheetAccessResolver}'s rule.
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;
    private final TimesheetReviewService reviewService;
    private final TimesheetSubmitService submitService;
    private final TimesheetProjectEntryService entryService;

    public TimesheetController(
            TimesheetService timesheetService,
            TimesheetReviewService reviewService,
            TimesheetSubmitService submitService,
            TimesheetProjectEntryService entryService) {
        this.timesheetService = Objects.requireNonNull(timesheetService, "timesheetService must not be null");
        this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
        this.submitService = Objects.requireNonNull(submitService, "submitService must not be null");
        this.entryService = Objects.requireNonNull(entryService, "entryService must not be null");
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

    /** Sends a draft week for approval, each project to its own manager (W-42.3). */
    @PutMapping("/{id}/submit")
    @RequiresAction(TimesheetServiceImpl.ACTION_SUBMIT)
    public ApiResponse<TimesheetResponse> submit(@PathVariable("id") UUID id) {
        return ApiResponse.success(submitService.submit(id));
    }

    /**
     * The one project line an approver is asked to decide. Approving and rejecting it are {@code core}'s decide
     * endpoint, on the step that the approver's pending list shows (W-42.3).
     */
    @GetMapping("/project-entries/{entryId}")
    @RequiresAction(TimesheetAccessResolver.ACTION_APPROVE)
    public ApiResponse<TimesheetProjectEntryResponse> projectEntry(@PathVariable("entryId") UUID entryId) {
        return ApiResponse.success(entryService.get(entryId));
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

    /** Weeks with a line on a project the caller manages, trimmed to those lines (W-42.4). */
    @GetMapping("/managed")
    @RequiresAction(TimesheetAccessResolver.ACTION_APPROVE)
    public ApiResponse<TimesheetPage> managed(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) TimesheetStatus status,
            @RequestParam(value = "projectId", required = false) UUID projectId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(reviewService.managed(from, to, status, projectId, page, size));
    }

    /** The whole week of each direct report (W-42.4). */
    @GetMapping("/team")
    @RequiresAction(TimesheetAccessResolver.ACTION_READ_TEAM)
    public ApiResponse<TimesheetPage> team(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) TimesheetStatus status,
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(reviewService.team(from, to, status, employeeId, page, size));
    }

    /** Every non-draft week in the tenant (W-42.4). */
    @GetMapping
    @RequiresAction(TimesheetAccessResolver.ACTION_READ)
    public ApiResponse<TimesheetPage> all(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) TimesheetStatus status,
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "projectId", required = false) UUID projectId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(reviewService.all(from, to, status, employeeId, projectId, page, size));
    }

    /**
     * One week: the caller's own, or as much of another's as {@link TimesheetAccessResolver} allows. Any of the four
     * read actions gets in; the resolver then decides, and a week the caller sees none of is a 404.
     */
    @GetMapping("/{id}")
    @RequiresAction(
            value = TimesheetServiceImpl.ACTION_READ_OWN,
            anyOf = {
                TimesheetAccessResolver.ACTION_READ_TEAM,
                TimesheetAccessResolver.ACTION_APPROVE,
                TimesheetAccessResolver.ACTION_READ
            })
    public ApiResponse<TimesheetResponse> get(@PathVariable("id") UUID id) {
        return ApiResponse.success(reviewService.get(id));
    }
}
