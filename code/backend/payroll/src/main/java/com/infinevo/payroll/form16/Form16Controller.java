package com.infinevo.payroll.form16;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing Form 16 Part B annual tax statement endpoints (W-36.4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class Form16Controller {

    private static final Logger log = LoggerFactory.getLogger(Form16Controller.class);

    private final Form16Service form16Service;

    public Form16Controller(Form16Service form16Service) {
        this.form16Service = Objects.requireNonNull(form16Service, "form16Service must not be null");
    }

    /**
     * Officer endpoint to read an employee's Form 16 statement for a financial year.
     */
    @GetMapping("/api/v1/payroll/employees/{employeeId}/form16/{fy}")
    @RequiresAction("payroll.statutory_report.read")
    public Form16Response get(@PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        log.info("Generating Form 16 statement for employee {} and financial year {}", employeeId, financialYear);
        return Form16Response.ok(
                "Form 16 statement retrieved successfully", form16Service.render(employeeId, financialYear));
    }

    /**
     * Employee self-service endpoint to read their own Form 16 statement for a financial year.
     */
    @GetMapping("/api/v1/me/form16/{fy}")
    @RequiresAction("payroll.payslip.read_own")
    public Form16Response getOwn(@PathVariable("fy") String financialYear) {
        log.info("Generating self-service Form 16 statement for financial year {}", financialYear);
        return Form16Response.ok("Form 16 statement retrieved successfully", form16Service.renderOwn(financialYear));
    }

    @ExceptionHandler({EmployeeTdsNotFoundException.class, EmployeeService.NotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DeductorNotSetException.class)
    public ResponseEntity<ApiErrorResponse> handleDeductorNotSet(DeductorNotSetException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
