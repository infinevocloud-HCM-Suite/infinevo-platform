package com.infinevo.hrms.performance;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Employee Goals & OKR tracking (W-106.2).
 */
@RestController
@RequestMapping("/api/v1/hrms/goals")
@RequiresModule(PlatformModule.HRMS)
public class EmployeeGoalController {

    private final EmployeeGoalService goalService;

    public EmployeeGoalController(EmployeeGoalService goalService) {
        this.goalService = Objects.requireNonNull(goalService, "goalService must not be null");
    }

    @GetMapping("/me")
    @RequiresAction("hrms.goal.read_own")
    public List<EmployeeGoalResponse> getMyGoals() {
        return goalService.getMyGoals();
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.goal.update_own")
    public EmployeeGoalResponse createGoal(@Valid @RequestBody CreateGoalRequest request) {
        return goalService.createGoal(request);
    }

    @PatchMapping("/me/{id}")
    @RequiresAction("hrms.goal.update_own")
    public EmployeeGoalResponse editGoal(@PathVariable("id") UUID id, @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.editGoal(id, request);
    }

    @PostMapping("/me/{id}/start")
    @RequiresAction("hrms.goal.update_own")
    public EmployeeGoalResponse startGoal(@PathVariable("id") UUID id) {
        return goalService.startGoal(id);
    }

    @PostMapping("/me/{id}/cancel")
    @RequiresAction("hrms.goal.update_own")
    public EmployeeGoalResponse cancelGoal(@PathVariable("id") UUID id) {
        return goalService.cancelGoal(id);
    }

    @PostMapping("/me/{id}/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.goal.update_own")
    public EmployeeGoalResponse checkIn(@PathVariable("id") UUID id, @Valid @RequestBody GoalCheckInRequest request) {
        return goalService.checkIn(id, request);
    }

    @GetMapping("/{id}/check-ins")
    @RequiresAction(
            value = "hrms.goal.read_own",
            anyOf = {"hrms.goal.review_team", "hrms.goal.manage"})
    public List<GoalCheckInResponse> getCheckIns(@PathVariable("id") UUID id) {
        return goalService.getCheckIns(id);
    }

    @GetMapping("/team")
    @RequiresAction("hrms.goal.review_team")
    public List<EmployeeGoalResponse> getTeamGoals() {
        return goalService.getTeamGoals();
    }

    @GetMapping("/employee/{employeeId}")
    @RequiresAction(
            value = "hrms.goal.review_team",
            anyOf = {"hrms.goal.manage"})
    public List<EmployeeGoalResponse> getGoalsForEmployee(
            @PathVariable("employeeId") UUID employeeId,
            @RequestParam(value = "cycleId", required = false) UUID cycleId) {
        return goalService.getGoalsForEmployee(employeeId, cycleId);
    }

    @GetMapping("/cycle/{cycleId}")
    @RequiresAction("hrms.goal.read_own")
    public List<EmployeeGoalResponse> getGoalsForCycle(@PathVariable("cycleId") UUID cycleId) {
        return goalService.getGoalsForCycle(cycleId);
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
