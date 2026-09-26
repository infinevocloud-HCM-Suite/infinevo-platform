package com.infinevo.shared.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.error.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-12.2 — Asserts that MODULE_NOT_ENTITLED, TENANT_SUSPENDED, and FORBIDDEN are three distinct
 * machine-readable codes and are not interchangeable (spec section 7).
 */
class RefusalDistinctionTest {

    @Test
    @DisplayName("MODULE_NOT_ENTITLED, TENANT_SUSPENDED, and FORBIDDEN have distinct codes and messages")
    void errorCodesAreDistinctAndNotInterchangeable() {
        assertThat(ApiError.MODULE_NOT_ENTITLED.code()).isEqualTo("MODULE_NOT_ENTITLED");
        assertThat(ApiError.TENANT_SUSPENDED.code()).isEqualTo("TENANT_SUSPENDED");
        assertThat(ApiError.FORBIDDEN.code()).isEqualTo("FORBIDDEN");

        assertThat(ApiError.MODULE_NOT_ENTITLED).isNotEqualTo(ApiError.FORBIDDEN);
        assertThat(ApiError.TENANT_SUSPENDED).isNotEqualTo(ApiError.FORBIDDEN);
        assertThat(ApiError.MODULE_NOT_ENTITLED).isNotEqualTo(ApiError.TENANT_SUSPENDED);

        assertThat(ApiError.MODULE_NOT_ENTITLED.defaultMessage()).isNotEqualTo(ApiError.FORBIDDEN.defaultMessage());
        assertThat(ApiError.TENANT_SUSPENDED.defaultMessage()).isNotEqualTo(ApiError.FORBIDDEN.defaultMessage());
        assertThat(ApiError.MODULE_NOT_ENTITLED.defaultMessage())
                .isNotEqualTo(ApiError.TENANT_SUSPENDED.defaultMessage());
    }

    @Test
    @DisplayName("EntitlementDeniedException preserves error code and module")
    void entitlementDeniedExceptionCarriesCodeAndModule() {
        EntitlementDeniedException notEntitled =
                new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, PlatformModule.HRMS);
        assertThat(notEntitled.error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED);
        assertThat(notEntitled.module()).isEqualTo(PlatformModule.HRMS);

        EntitlementDeniedException suspended =
                new EntitlementDeniedException(ApiError.TENANT_SUSPENDED, PlatformModule.PAYROLL);
        assertThat(suspended.error()).isEqualTo(ApiError.TENANT_SUSPENDED);
        assertThat(suspended.module()).isEqualTo(PlatformModule.PAYROLL);
    }
}
