package com.infinevo.core.invitation;

import com.infinevo.core.authz.RoleService;
import com.infinevo.core.employee.EmployeeService;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for public invitation acceptance and decline endpoints (W-24.2, spec section 4).
 *
 * <p>Unauthenticated endpoints: the bearer token is the single-use credential.
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationAcceptanceController {

    private static final Logger log = LoggerFactory.getLogger(InvitationAcceptanceController.class);

    private final InvitationService invitationService;

    public InvitationAcceptanceController(InvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
    }

    /**
     * The one answer for every token that cannot be used — unknown, accepted, declined, revoked or expired.
     * A distinct message per state would let a caller probe which tokens exist (spec §9).
     */
    static final String GENERIC_FAILURE =
            "This invitation cannot be used. It may have expired, been used or been withdrawn. Ask for a new invitation.";

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@RequestBody AcceptInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            return badRequest("token must not be blank");
        }
        AcceptOutcome outcome = invitationService.acceptInvitation(request.token());
        return ResponseEntity.ok(new AcceptInvitationResponse("Invitation accepted successfully", outcome));
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@RequestBody DeclineInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            return badRequest("token must not be blank");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            return badRequest("reason must not be blank");
        }
        if (request.reason().length() > 500) {
            return badRequest("reason cannot exceed 500 characters");
        }
        invitationService.declineInvitation(request.token(), request.reason());
        return ResponseEntity.ok(new InvitationMessageResponse("Invitation declined successfully"));
    }

    @ExceptionHandler(KeycloakProvisioningException.class)
    public ResponseEntity<ApiErrorResponse> handleKeycloakProvisioning(KeycloakProvisioningException e) {
        // The reply is deliberately generic; the log carries the reason so the failure can be diagnosed.
        log.warn("Invitation request failed at Keycloak (traceId {}): {}", traceId(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(
                        ApiError.INTERNAL,
                        "Identity service is currently unavailable. Please try again later.",
                        traceId()));
    }

    /**
     * Every refusal the service raises for the token itself, and the lookups acceptance performs, get the
     * same generic answer — never the service's message, never a 500.
     */
    @ExceptionHandler({
        IllegalArgumentException.class,
        IllegalStateException.class,
        RoleService.NotFoundException.class,
        RoleService.ValidationException.class,
        RoleService.SystemRoleException.class,
        EmployeeService.NotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnusableInvitation(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, GENERIC_FAILURE, traceId()));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, message, traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception e) {
        // The reply is deliberately generic; the log carries the reason so the failure can be diagnosed.
        log.error("Invitation request failed (traceId {})", traceId(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        ApiError.INTERNAL, "An unexpected error occurred while processing the invitation.", traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
