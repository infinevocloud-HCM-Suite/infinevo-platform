package com.infinevo.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

    @Test
    @DisplayName("ApiError enum constants have matching codes and non-empty default messages")
    void apiErrorCodesAndMessages() {
        assertThat(ApiError.IMPERSONATION_INVALID.code()).isEqualTo("IMPERSONATION_INVALID");
        assertThat(ApiError.IMPERSONATION_INVALID.defaultMessage()).isNotBlank();

        assertThat(ApiError.USER_NOT_FOUND.code()).isEqualTo("USER_NOT_FOUND");
        assertThat(ApiError.USER_NOT_FOUND.defaultMessage()).isNotBlank();

        assertThat(ApiError.TENANT_NOT_FOUND.code()).isEqualTo("TENANT_NOT_FOUND");
        assertThat(ApiError.TENANT_NOT_FOUND.defaultMessage()).isNotBlank();
    }
}
