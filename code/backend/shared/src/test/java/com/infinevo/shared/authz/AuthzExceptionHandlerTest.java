package com.infinevo.shared.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

/** D-91: a missing employee record is reported as that, not as a missing action. */
class AuthzExceptionHandlerTest {

    private final AuthzExceptionHandler handler = new AuthzExceptionHandler();

    @Test
    @DisplayName("A denied action names the action")
    void deniedActionNamesTheAction() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(new PermissionDeniedException("hrms.timesheet.read_own"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Not permitted: requires action 'hrms.timesheet.read_own'");
    }

    @Test
    @DisplayName("No employee record: the message says so and names no action (D-91)")
    void noEmployeeRecordSaysSo() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(new NoEmployeeRecordException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ApiError.FORBIDDEN.name());
        assertThat(response.getBody().message())
                .isEqualTo(NoEmployeeRecordException.MESSAGE)
                .doesNotContain("requires action");
    }

    @Test
    @DisplayName("Any other access denial stays the plain 'Not permitted'")
    void otherDenialIsPlain() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("internal detail"));

        assertThat(response.getBody().message()).isEqualTo("Not permitted");
    }
}
