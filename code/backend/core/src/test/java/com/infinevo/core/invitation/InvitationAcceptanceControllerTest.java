package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InvitationAcceptanceControllerTest {

    private InvitationService invitationService;
    private InvitationAcceptanceController controller;

    @BeforeEach
    void setUp() {
        invitationService = mock(InvitationService.class);
        controller = new InvitationAcceptanceController(invitationService);
    }

    @Test
    @DisplayName("accept delegates to invitationService")
    void acceptDelegatesToService() {
        AcceptInvitationRequest request = new AcceptInvitationRequest("valid-token");
        ResponseEntity<InvitationMessageResponse> response = controller.accept(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("accepted successfully");
        verify(invitationService).acceptInvitation("valid-token");
    }

    @Test
    @DisplayName("decline delegates to invitationService")
    void declineDelegatesToService() {
        DeclineInvitationRequest request = new DeclineInvitationRequest("valid-token", "Not interested");
        ResponseEntity<InvitationMessageResponse> response = controller.decline(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("declined successfully");
        verify(invitationService).declineInvitation("valid-token", "Not interested");
    }

    @Test
    @DisplayName("handleKeycloakProvisioning: returns 503 SERVICE_UNAVAILABLE")
    void handleKeycloakProvisioningReturns503() {
        KeycloakProvisioningException ex = new KeycloakProvisioningException("Connection refused");
        ResponseEntity<ApiErrorResponse> response = controller.handleKeycloakProvisioning(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiError.INTERNAL.name());
        assertThat(response.getBody().message()).doesNotContain("Connection refused");
        assertThat(response.getBody().message()).contains("Identity service");
    }

    @Test
    @DisplayName("handleIllegalState: preserves client-facing messages")
    void handleIllegalStatePreservesClientFacingMessages() {
        IllegalStateException ex = new IllegalStateException("Invitation has already been accepted");
        ResponseEntity<ApiErrorResponse> response = controller.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invitation has already been accepted");
    }

    @Test
    @DisplayName("handleIllegalState: sanitizes internal details containing tenant or system IDs")
    void handleIllegalStateSanitizesInternalDetails() {
        IllegalStateException ex = new IllegalStateException(
                "Seeded 'employee' role not found in tenant 123e4567-e89b-12d3-a456-426614174000");
        ResponseEntity<ApiErrorResponse> response = controller.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("123e4567-e89b-12d3-a456-426614174000");
        assertThat(response.getBody().message()).doesNotContain("tenant");
        assertThat(response.getBody().message()).contains("Unable to process invitation");
    }

    @Test
    @DisplayName("handleGeneralException: returns 500 with generic message")
    void handleGeneralExceptionReturns500() {
        RuntimeException ex = new RuntimeException("Unexpected DB connection pool exhausted");
        ResponseEntity<ApiErrorResponse> response = controller.handleGeneralException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiError.INTERNAL.name());
        assertThat(response.getBody().message()).doesNotContain("DB connection pool exhausted");
        assertThat(response.getBody().message()).contains("An unexpected error occurred");
    }
}
