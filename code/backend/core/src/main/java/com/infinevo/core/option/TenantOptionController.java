package com.infinevo.core.option;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for tenant options management (W-104 §4).
 */
@RestController
@RequestMapping("/api/v1/tenant-options")
public class TenantOptionController {

    private final TenantOptionService tenantOptionService;

    public TenantOptionController(TenantOptionService tenantOptionService) {
        this.tenantOptionService = Objects.requireNonNull(tenantOptionService, "tenantOptionService must not be null");
    }

    @GetMapping
    @RequiresAction("core.tenant_option.read")
    public List<TenantOptionResponse> list(
            @RequestParam(value = "category", required = false) OptionCategory category) {
        return tenantOptionService.list(category);
    }

    @PutMapping("/{key}")
    @RequiresAction("core.tenant_option.manage")
    public TenantOptionResponse set(@PathVariable("key") String key, @RequestBody TenantOptionUpdateRequest request) {
        if (request == null || request.value() == null) {
            throw new TenantOptionService.OptionValidationException("Request body and value must not be null");
        }
        return tenantOptionService.set(key, request.value());
    }

    @DeleteMapping("/{key}")
    @RequiresAction("core.tenant_option.manage")
    public TenantOptionResponse reset(@PathVariable("key") String key) {
        return tenantOptionService.reset(key);
    }

    @ExceptionHandler(TenantOptionService.NoSuchOptionException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TenantOptionService.NoSuchOptionException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler({TenantOptionService.OptionValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception e) {
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
