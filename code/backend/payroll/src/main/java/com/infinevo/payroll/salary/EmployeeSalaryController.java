package com.infinevo.payroll.salary;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee salary structures and effective-dated revisions (W-26.2).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/employees/{employeeId}/salary")
public class EmployeeSalaryController {

    private final EmployeeSalaryService employeeSalaryService;

    public EmployeeSalaryController(EmployeeSalaryService employeeSalaryService) {
        this.employeeSalaryService =
                Objects.requireNonNull(employeeSalaryService, "employeeSalaryService must not be null");
    }

    @PostMapping
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<SalaryVersionResponse> create(
            @PathVariable("employeeId") UUID employeeId, @RequestBody SalaryVersionRequest request) {
        SalaryVersionResponse created = employeeSalaryService.create(employeeId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/employees/" + employeeId + "/salary/versions/" + created.id()))
                .body(created);
    }

    @PostMapping("/revisions")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<SalaryVersionResponse> revise(
            @PathVariable("employeeId") UUID employeeId, @RequestBody SalaryVersionRequest request) {
        SalaryVersionResponse created = employeeSalaryService.revise(employeeId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/employees/" + employeeId + "/salary/versions/" + created.id()))
                .body(created);
    }

    @GetMapping
    @RequiresAction("payroll.salary.read")
    public SalaryVersionResponse getAsOf(
            @PathVariable("employeeId") UUID employeeId,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return employeeSalaryService.getAsOf(employeeId, asOf);
    }

    @GetMapping("/versions")
    @RequiresAction("payroll.salary.read")
    public List<SalaryVersionResponse> listVersions(@PathVariable("employeeId") UUID employeeId) {
        return employeeSalaryService.listVersions(employeeId);
    }

    @PutMapping("/versions/{id}")
    @RequiresAction("payroll.salary.manage")
    public SalaryVersionResponse update(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("id") UUID id,
            @RequestBody SalaryVersionRequest request) {
        return employeeSalaryService.update(employeeId, id, request);
    }

    @DeleteMapping("/versions/{id}")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<Void> cancel(@PathVariable("employeeId") UUID employeeId, @PathVariable("id") UUID id) {
        employeeSalaryService.cancel(employeeId, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SalaryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SalaryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SalaryValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(SalaryValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
    }

    @ExceptionHandler(SalaryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(SalaryConflictException e) {
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
