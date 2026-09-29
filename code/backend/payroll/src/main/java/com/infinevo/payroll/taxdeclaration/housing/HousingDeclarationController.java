package com.infinevo.payroll.taxdeclaration.housing;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HousingDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for payroll officer operations on employee housing declarations (W-32.2).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}")
public class HousingDeclarationController {

    private final HousingDeclarationService housingDeclarationService;

    public HousingDeclarationController(HousingDeclarationService housingDeclarationService) {
        this.housingDeclarationService =
                Objects.requireNonNull(housingDeclarationService, "housingDeclarationService must not be null");
    }

    @GetMapping("/housing")
    @RequiresAction("payroll.tax_declaration.read")
    public ApiResponse<HousingDeclarationResponse> get(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        HousingDeclarationResponse response = housingDeclarationService.read(employeeId, financialYear);
        return ApiResponse.ok("Housing declaration retrieved successfully", response);
    }

    @PutMapping("/house-rent")
    @RequiresAction("payroll.tax_declaration.manage")
    public ApiResponse<HousingDeclarationResponse> replaceHouseRent(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @RequestBody List<HouseRentRequest> requests) {
        HousingDeclarationResponse response =
                housingDeclarationService.replaceHouseRent(employeeId, financialYear, requests);
        return ApiResponse.ok("House rent declaration updated successfully", response);
    }

    @PutMapping("/home-loan")
    @RequiresAction("payroll.tax_declaration.manage")
    public ApiResponse<HousingDeclarationResponse> replaceHomeLoans(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @RequestBody List<HomeLoanRequest> requests) {
        HousingDeclarationResponse response =
                housingDeclarationService.replaceHomeLoans(employeeId, financialYear, requests);
        return ApiResponse.ok("Home loan declaration updated successfully", response);
    }

    @PutMapping("/let-out-property")
    @RequiresAction("payroll.tax_declaration.manage")
    public ApiResponse<HousingDeclarationResponse> replaceLetOutProperties(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @RequestBody List<LetOutPropertyRequest> requests) {
        HousingDeclarationResponse response =
                housingDeclarationService.replaceLetOutProperties(employeeId, financialYear, requests);
        return ApiResponse.ok("Let-out property declaration updated successfully", response);
    }

    @ExceptionHandler(DeclarationNotEditableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotEditable(DeclarationNotEditableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(e.reasonCode(), e.getMessage(), Map.of(), traceId(), Instant.now()));
    }

    @ExceptionHandler(WindowValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(WindowValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(DeclarationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DeclarationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
