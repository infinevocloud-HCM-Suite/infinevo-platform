package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.RoleService;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import java.util.List;
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
    @DisplayName("accept delegates to invitationService and returns what the invitee does next (D-62)")
    void acceptDelegatesToService() {
        when(invitationService.acceptInvitation("valid-token", "Str0ng-Passw0rd!"))
                .thenReturn(AcceptOutcome.EXISTING_ACCOUNT);
        AcceptInvitationRequest request = new AcceptInvitationRequest("valid-token", "Str0ng-Passw0rd!");
        ResponseEntity<?> response = controller.accept(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AcceptInvitationResponse body = (AcceptInvitationResponse) response.getBody();
        assertThat(body.message()).contains("accepted successfully");
        assertThat(body.outcome()).isEqualTo(AcceptOutcome.EXISTING_ACCOUNT);
        verify(invitationService).acceptInvitation("valid-token", "Str0ng-Passw0rd!");
    }

    @Test
    @DisplayName("decline delegates to invitationService")
    void declineDelegatesToService() {
        DeclineInvitationRequest request = new DeclineInvitationRequest("valid-token", "Not interested");
        ResponseEntity<?> response = controller.decline(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((InvitationMessageResponse) response.getBody()).message()).contains("declined successfully");
        verify(invitationService).declineInvitation("valid-token", "Not interested");
    }

    @Test
    @DisplayName(
            "a blank token, a blank or oversized password, or a blank reason is a 400 and never reaches the service")
    void blankInputIsBadRequest() {
        assertThat(controller
                        .accept(new AcceptInvitationRequest(" ", "Str0ng-Passw0rd!"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.accept(new AcceptInvitationRequest("t", " ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.accept(new AcceptInvitationRequest("t", null)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller
                        .accept(new AcceptInvitationRequest("t", "x".repeat(129)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.decline(new DeclineInvitationRequest("t", " ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("invalid, accepted, revoked, declined, expired and not-found all get one identical answer")
    void everyUnusableTokenGetsTheSameAnswer() {
        List<RuntimeException> failures = List.of(
                new IllegalArgumentException("Invalid invitation token"),
                new IllegalStateException("Invitation has already been accepted"),
                new IllegalStateException("Invitation has been revoked"),
                new IllegalStateException("Invitation has been declined"),
                new InvitationExpiredException(),
                new IllegalStateException("Seeded 'employee' role not found in tenant 123e4567"),
                new RoleService.NotFoundException("No role 42 in this tenant"),
                new EmployeeService.NotFoundException(java.util.UUID.randomUUID()));
        for (RuntimeException failure : failures) {
            ResponseEntity<ApiErrorResponse> response = controller.handleUnusableInvitation(failure);
            assertThat(response.getStatusCode()).as(failure.toString()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ApiError.CONFLICT.name());
            assertThat(response.getBody().message()).isEqualTo(InvitationAcceptanceController.GENERIC_FAILURE);
        }
    }

    @Test
    @DisplayName("D-88: a password Keycloak refused is a 400 that names the rule, not the generic invitation answer")
    void refusedPasswordIsAValidationFailureWithTheRule() {
        ResponseEntity<ApiErrorResponse> response =
                controller.handlePasswordPolicy(new PasswordPolicyException("Invalid password: minimum length 10."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ApiError.VALIDATION_FAILED.name());
        assertThat(response.getBody().message()).isEqualTo("Invalid password: minimum length 10.");
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
