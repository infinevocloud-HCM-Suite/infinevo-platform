package com.infinevo.payroll.scheduled;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-73.6 §4. Five mappings: list and add on the employee, pause, resume and cancel on the row.
 * Reads need {@code payroll.structure.read}; writes {@code payroll.structure.manage} — the catalogue's
 * existing write verb for salary structure ({@code V022}); the spec's {@code payroll.structure.update}
 * names no action.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class ScheduledEarningController {

    static final String EMPLOYEE_PATH = "/api/v1/payroll/employees/{employeeId}/scheduled-earnings";
    static final String ROW_PATH = "/api/v1/payroll/scheduled-earnings/{id}";

    private final ScheduledEarningService service;

    public ScheduledEarningController(ScheduledEarningService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping(EMPLOYEE_PATH)
    @RequiresAction("payroll.structure.read")
    public List<ScheduledEarningResponse> list(@PathVariable("employeeId") UUID employeeId) {
        return service.listForEmployee(employeeId);
    }

    @PostMapping(EMPLOYEE_PATH)
    @RequiresAction("payroll.structure.manage")
    public ResponseEntity<ScheduledEarningResponse> create(
            @PathVariable("employeeId") UUID employeeId, @RequestBody ScheduledEarningRequest request) {
        ScheduledEarningResponse created = service.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/scheduled-earnings/" + created.id()))
                .body(created);
    }

    @PostMapping(ROW_PATH + "/pause")
    @RequiresAction("payroll.structure.manage")
    public ScheduledEarningResponse pause(
            @PathVariable("id") UUID id, @RequestBody(required = false) ScheduledEarningActionRequest body) {
        return service.pause(id, body != null ? body.reason() : null);
    }

    @PostMapping(ROW_PATH + "/resume")
    @RequiresAction("payroll.structure.manage")
    public ScheduledEarningResponse resume(
            @PathVariable("id") UUID id, @RequestBody(required = false) ScheduledEarningActionRequest body) {
        return service.resume(id);
    }

    @PostMapping(ROW_PATH + "/cancel")
    @RequiresAction("payroll.structure.manage")
    public ScheduledEarningResponse cancel(
            @PathVariable("id") UUID id, @RequestBody(required = false) ScheduledEarningActionRequest body) {
        return service.cancel(id, body != null ? body.reason() : null);
    }

    @ExceptionHandler(ScheduledEarningService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ScheduledEarningService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ScheduledEarningService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ScheduledEarningService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(ScheduledEarningService.IllegalTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleTransition(ScheduledEarningService.IllegalTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
