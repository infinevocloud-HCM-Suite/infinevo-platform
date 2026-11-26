package com.infinevo.hrms.asset;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/assets")
public class MyAssetController {

    private final AssetService assetService;
    private final EmployeeService employeeService;

    public MyAssetController(AssetService assetService, EmployeeService employeeService) {
        this.assetService = Objects.requireNonNull(assetService, "assetService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('hrms.asset.read_own')")
    public ApiResponse<List<AssetResponse>> getMyAssets() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse currentEmployee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AssetValidationException("Current employee profile could not be resolved"));

        List<AssetResponse> assets = assetService.getAssignedAssetsForEmployee(tenantId, currentEmployee.id());
        return ApiResponse.success(assets);
    }

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AssetNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(AssetConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(AssetConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(AssetValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(AssetValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
