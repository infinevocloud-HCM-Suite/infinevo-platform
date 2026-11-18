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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for self-reviews and manager evaluations (W-106.1).
 */
@RestController
@RequestMapping("/api/v1/performance/reviews")
@RequiresModule(PlatformModule.HRMS)
public class PerformanceReviewController {

    private final PerformanceReviewService performanceService;

    public PerformanceReviewController(PerformanceReviewService performanceService) {
        this.performanceService = Objects.requireNonNull(performanceService, "performanceService must not be null");
    }

    @GetMapping("/my")
    @RequiresAction("hrms.performance.read_own")
    public List<PerformanceReviewResponse> getMyReviews() {
        return performanceService.getMyReviews();
    }

    @PatchMapping("/{id}/self-submit")
    @RequiresAction("hrms.performance.update_own")
    public PerformanceReviewResponse submitSelfReview(
            @PathVariable("id") UUID id, @Valid @RequestBody SelfReviewRequest request) {
        return performanceService.submitSelfReview(id, request);
    }

    @GetMapping("/team")
    @RequiresAction("hrms.performance.review_team")
    public List<PerformanceReviewResponse> getTeamReviews() {
        return performanceService.getTeamReviews();
    }

    @PatchMapping("/{id}/manager-submit")
    @RequiresAction("hrms.performance.review_team")
    public PerformanceReviewResponse submitManagerReview(
            @PathVariable("id") UUID id, @Valid @RequestBody ManagerReviewRequest request) {
        return performanceService.submitManagerReview(id, request);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
