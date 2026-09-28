package com.infinevo.payroll.fbp;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Flexible Benefit Plan configuration and components (W-27.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/fbp")
public class FbpPlanController {

    private final FbpPlanService fbpPlanService;

    public FbpPlanController(FbpPlanService fbpPlanService) {
        this.fbpPlanService = Objects.requireNonNull(fbpPlanService, "fbpPlanService must not be null");
    }

    @GetMapping("/plan")
    @RequiresAction("payroll.structure.read")
    public FbpPlanResponse getPlan() {
        return fbpPlanService.get();
    }

    @PutMapping("/plan")
    @RequiresAction("payroll.settings.manage")
    public FbpPlanResponse updatePlan(@RequestBody FbpPlanRequest request) {
        return fbpPlanService.upsert(request);
    }

    @PostMapping("/plan/lock")
    @RequiresAction("payroll.settings.manage")
    public FbpPlanResponse lockPlan() {
        return fbpPlanService.lock();
    }

    @PostMapping("/plan/unlock")
    @RequiresAction("payroll.settings.manage")
    public FbpPlanResponse unlockPlan() {
        return fbpPlanService.unlock();
    }

    @GetMapping("/components")
    @RequiresAction("payroll.structure.read")
    public List<FbpComponentResponse> getComponents() {
        return fbpPlanService.components();
    }

    @ExceptionHandler(FbpValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(FbpValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
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
