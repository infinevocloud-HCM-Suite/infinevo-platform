package com.infinevo.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcLoggingContextTest {

    @BeforeEach
    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Binds and unbinds correlationId using try-with-resources")
    void shouldBindAndUnbindCorrelationId() {
        try (var ctx = MdcLoggingContext.withCorrelationId("corr-123")) {
            assertThat(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)).isEqualTo("corr-123");
        }
        assertThat(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Binds and unbinds tenantId using try-with-resources")
    void shouldBindAndUnbindTenantId() {
        UUID tenantId = UUID.randomUUID();
        try (var ctx = MdcLoggingContext.withTenantId(tenantId)) {
            assertThat(MDC.get(MdcLoggingContext.TENANT_ID_KEY)).isEqualTo(tenantId.toString());
        }
        assertThat(MDC.get(MdcLoggingContext.TENANT_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Binds and unbinds userId using try-with-resources")
    void shouldBindAndUnbindUserId() {
        UUID userId = UUID.randomUUID();
        try (var ctx = MdcLoggingContext.withUserId(userId)) {
            assertThat(MDC.get(MdcLoggingContext.USER_ID_KEY)).isEqualTo(userId.toString());
        }
        assertThat(MDC.get(MdcLoggingContext.USER_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Binds and unbinds acting_as using try-with-resources")
    void shouldBindAndUnbindActingAs() {
        UUID staffId = UUID.randomUUID();
        try (var ctx = MdcLoggingContext.withActingAs(staffId)) {
            assertThat(MDC.get(MdcLoggingContext.ACTING_AS_KEY)).isEqualTo(staffId.toString());
        }
        assertThat(MDC.get(MdcLoggingContext.ACTING_AS_KEY)).isNull();
    }
}
