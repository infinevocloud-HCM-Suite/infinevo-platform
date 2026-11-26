package com.infinevo.hrms.asset;

import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hrms")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = Objects.requireNonNull(assetService, "assetService must not be null");
    }

    @GetMapping("/assets")
    @PreAuthorize("hasAuthority('hrms.asset.read')")
    public ApiResponse<List<AssetResponse>> listAssets(
            @RequestParam(name = "status", required = false) AssetStatus status,
            @RequestParam(name = "category", required = false) AssetCategory category) {
        UUID tenantId = TenantContext.require();
        List<AssetResponse> assets = assetService.listAssets(tenantId, status, category);
        return ApiResponse.success(assets);
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('hrms.asset.manage')")
    public ApiResponse<AssetResponse> createAsset(@RequestBody AssetRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        AssetResponse created = assetService.createAsset(tenantId, request, user);
        return ApiResponse.success("Asset registered successfully", created);
    }

    @GetMapping("/assets/{id}")
    @PreAuthorize("hasAuthority('hrms.asset.read')")
    public ApiResponse<AssetResponse> getAsset(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        AssetResponse asset = assetService.getAsset(tenantId, id);
        return ApiResponse.success(asset);
    }

    @PutMapping("/assets/{id}")
    @PreAuthorize("hasAuthority('hrms.asset.manage')")
    public ApiResponse<AssetResponse> updateAsset(
            @PathVariable("id") UUID id, @RequestBody AssetRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        AssetResponse updated = assetService.updateAsset(tenantId, id, request, user);
        return ApiResponse.success("Asset updated successfully", updated);
    }

    @PatchMapping("/assets/{id}/status")
    @PreAuthorize("hasAuthority('hrms.asset.manage')")
    public ApiResponse<AssetResponse> updateStatus(
            @PathVariable("id") UUID id, @RequestBody AssetStatusUpdateRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        AssetResponse updated = assetService.updateStatus(tenantId, id, request, user);
        return ApiResponse.success("Asset status updated", updated);
    }

    @PostMapping("/assets/{id}/assign")
    @PreAuthorize("hasAuthority('hrms.asset.manage')")
    public ApiResponse<AssetResponse> assignAsset(
            @PathVariable("id") UUID id, @RequestBody AssetAssignRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        AssetResponse assigned = assetService.assignAsset(tenantId, id, request, user);
        return ApiResponse.success("Asset assigned successfully", assigned);
    }

    @PostMapping("/assets/{id}/return")
    @PreAuthorize("hasAuthority('hrms.asset.manage')")
    public ApiResponse<AssetResponse> returnAsset(
            @PathVariable("id") UUID id, @RequestBody AssetReturnRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        AssetResponse returned = assetService.returnAsset(tenantId, id, request, user);
        return ApiResponse.success("Asset returned successfully", returned);
    }

    @GetMapping("/assets/{id}/history")
    @PreAuthorize("hasAuthority('hrms.asset.read')")
    public ApiResponse<List<AssetAssignmentResponse>> getAssignmentHistory(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        List<AssetAssignmentResponse> history = assetService.getAssignmentHistory(tenantId, id);
        return ApiResponse.success(history);
    }

    @GetMapping({"/employees/{id}/assets", "/employees/{id}/assets/active"})
    @PreAuthorize("hasAuthority('hrms.asset.read')")
    public ApiResponse<EmployeeClearanceResponse> getEmployeeClearance(@PathVariable("id") UUID employeeId) {
        UUID tenantId = TenantContext.require();
        EmployeeClearanceResponse clearance = assetService.getEmployeeClearance(tenantId, employeeId);
        return ApiResponse.success(clearance);
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.validation(errors, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
