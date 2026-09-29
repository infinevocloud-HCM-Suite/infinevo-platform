package com.infinevo.core.invitation;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
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

    private final InvitationService invitationService;

    public InvitationAcceptanceController(InvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
    }

    @PostMapping("/accept")
    public ResponseEntity<InvitationMessageResponse> accept(@RequestBody AcceptInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        invitationService.acceptInvitation(request.token());
        return ResponseEntity.ok(new InvitationMessageResponse("Invitation accepted successfully"));
    }

    @PostMapping("/decline")
    public ResponseEntity<InvitationMessageResponse> decline(@RequestBody DeclineInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        invitationService.declineInvitation(request.token(), request.reason());
        return ResponseEntity.ok(new InvitationMessageResponse("Invitation declined successfully"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Invalid request";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, msg, traceId()));
    }

    @ExceptionHandler(KeycloakProvisioningException.class)
    public ResponseEntity<ApiErrorResponse> handleKeycloakProvisioning(KeycloakProvisioningException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(
                        ApiError.INTERNAL,
                        "Identity service is currently unavailable. Please try again later.",
                        traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = "Unable to process invitation.";
        } else if (msg.contains("tenant")
                || msg.contains("not found")
                || msg.contains("user account")
                || msg.contains("role")) {
            // Sanitize internal system/database details
            msg = "Unable to process invitation. Please contact support or request a new invitation.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(ApiError.CONFLICT, msg, traceId()));
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
