package com.infinevo.payroll.reimbursement;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee reimbursement claims (W-35.1).
 * Six endpoints: 4 for employee self-service under /api/v1/me (the claimable-components read is W-47.4 §4),
 * 2 for payroll officer under /api/v1/payroll. Approval actions belong to core approvals.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class ReimbursementClaimController {

    private final ReimbursementClaimService reimbursementClaimService;

    public ReimbursementClaimController(ReimbursementClaimService reimbursementClaimService) {
        this.reimbursementClaimService =
                Objects.requireNonNull(reimbursementClaimService, "reimbursementClaimService must not be null");
    }

    @PostMapping("/api/v1/me/reimbursement-claims")
    @RequiresAction("payroll.reimbursement_claim.submit_own")
    public ResponseEntity<ReimbursementApiResponse<ReimbursementClaimResponse>> submitOwn(
            @RequestBody ReimbursementClaimRequest request) {
        ReimbursementClaimResponse created = reimbursementClaimService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReimbursementApiResponse.created("Reimbursement claim submitted successfully", created));
    }

    @GetMapping("/api/v1/me/reimbursement-claims")
    @RequiresAction("payroll.reimbursement_claim.read_own")
    public ResponseEntity<ReimbursementApiResponse<List<ReimbursementClaimResponse>>> listOwn() {
        List<ReimbursementClaimResponse> claims = reimbursementClaimService.listOwn();
        return ResponseEntity.ok(ReimbursementApiResponse.ok("Reimbursement claims retrieved successfully", claims));
    }

    /** W-47.4 §4. A literal segment, so Spring prefers it over {@code /{id}}. */
    @GetMapping("/api/v1/me/reimbursement-claims/components")
    @RequiresAction("payroll.reimbursement_claim.submit_own")
    public ResponseEntity<ReimbursementApiResponse<List<ClaimableComponentResponse>>> claimableComponents() {
        List<ClaimableComponentResponse> components = reimbursementClaimService.claimableComponents();
        return ResponseEntity.ok(
                ReimbursementApiResponse.ok("Claimable reimbursement components retrieved successfully", components));
    }

    @GetMapping("/api/v1/me/reimbursement-claims/{id}")
    @RequiresAction("payroll.reimbursement_claim.read_own")
    public ResponseEntity<ReimbursementApiResponse<ReimbursementClaimResponse>> getOwn(@PathVariable("id") UUID id) {
        ReimbursementClaimResponse claim = reimbursementClaimService.getOwn(id);
        return ResponseEntity.ok(ReimbursementApiResponse.ok("Reimbursement claim retrieved successfully", claim));
    }

    @GetMapping("/api/v1/payroll/reimbursement-claims")
    @RequiresAction("payroll.reimbursement_claim.read")
    public ResponseEntity<ReimbursementApiResponse<Page<ReimbursementClaimResponse>>> list(
            @RequestParam(name = "employeeId", required = false) UUID employeeId,
            @RequestParam(name = "status", required = false) ClaimStatus status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        int clampedSize = Math.min(Math.max(1, size), 100);
        PageRequest pageable =
                PageRequest.of(Math.max(0, page), clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReimbursementClaimResponse> result =
                reimbursementClaimService.list(employeeId, status, from, to, pageable);
        return ResponseEntity.ok(ReimbursementApiResponse.ok("Reimbursement claims retrieved successfully", result));
    }

    @GetMapping("/api/v1/payroll/reimbursement-claims/{id}")
    @RequiresAction("payroll.reimbursement_claim.read")
    public ResponseEntity<ReimbursementApiResponse<ReimbursementClaimResponse>> get(@PathVariable("id") UUID id) {
        ReimbursementClaimResponse claim = reimbursementClaimService.get(id);
        return ResponseEntity.ok(ReimbursementApiResponse.ok("Reimbursement claim retrieved successfully", claim));
    }

    @ExceptionHandler(ReimbursementClaimNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ReimbursementClaimNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(ReimbursementClaimValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ReimbursementClaimValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed", getTraceId()));
    }

    private String getTraceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
