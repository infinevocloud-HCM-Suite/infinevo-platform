package com.infinevo.shared.impersonation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.shared.tenant.PlatformTenant;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class ImpersonationServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ImpersonationService service;

    private final UUID customerTenantId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ImpersonationService(jdbcTemplate);
    }

    @Test
    @DisplayName("Opening impersonation for the platform tenant itself is refused with 400 validation error")
    void openImpersonation_refusesPlatformTenant() {
        assertThatThrownBy(() -> service.openImpersonation(
                        PlatformTenant.DEFAULT_PLATFORM_TENANT_ID,
                        staffUserId,
                        targetUserId,
                        null,
                        "Investigating support ticket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot impersonate platform tenant");
    }

    @Test
    @DisplayName("Opening impersonation with blank reason is refused")
    void openImpersonation_refusesBlankReason() {
        assertThatThrownBy(() -> service.openImpersonation(customerTenantId, staffUserId, targetUserId, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason must not be blank");

        assertThatThrownBy(() -> service.openImpersonation(customerTenantId, staffUserId, targetUserId, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason must not be blank");
    }

    @Test
    @DisplayName("Opening impersonation with reason exceeding 200 characters is refused")
    void openImpersonation_refusesReasonExceeding200Chars() {
        String longReason = "a".repeat(201);
        assertThatThrownBy(
                        () -> service.openImpersonation(customerTenantId, staffUserId, targetUserId, null, longReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason must not exceed 200 characters");
    }

    @Test
    @DisplayName("Opening impersonation calls the database function and maps the row it returns")
    @SuppressWarnings("unchecked")
    void openImpersonation_success_callsDatabaseAndReturnsSession() {
        // The expiry is whatever the row says. The thirty-minute lifetime is set by core.open_impersonation, so
        // a mock cannot prove it; ImpersonationIT and BootstrapSessionIT read it back from the stored session.
        Instant expectedExpiry = Instant.now().plus(30, ChronoUnit.MINUTES);
        ImpersonationService.OpenedImpersonation mockResult = new ImpersonationService.OpenedImpersonation(
                sessionId, customerTenantId, targetUserId, "user@customer.local", expectedExpiry);

        given(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        eq(customerTenantId),
                        eq(staffUserId),
                        eq(targetUserId),
                        eq("user@customer.local"),
                        eq("Investigating payroll bug")))
                .willReturn(mockResult);

        ImpersonationService.OpenedImpersonation opened = service.openImpersonation(
                customerTenantId, staffUserId, targetUserId, "user@customer.local", "Investigating payroll bug");

        assertThat(opened.sessionId()).isEqualTo(sessionId);
        assertThat(opened.tenantId()).isEqualTo(customerTenantId);
        assertThat(opened.userAccountId()).isEqualTo(targetUserId);
        assertThat(opened.userEmail()).isEqualTo("user@customer.local");
        assertThat(opened.expiresAt()).isEqualTo(expectedExpiry);
        assertThat(opened.isBootstrap()).isFalse();
    }

    @Test
    @DisplayName("Opening impersonation maps P0004 database exception to UserNotFoundException")
    @SuppressWarnings("unchecked")
    void openImpersonation_handlesUserNotFound() {
        SQLException sqlEx = new SQLException("Target user not found: notfound@customer.local", "P0004");
        DataAccessException daEx = new TestDataAccessException("Database query failed", sqlEx);

        given(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        eq(customerTenantId),
                        eq(staffUserId),
                        any(),
                        any(),
                        any()))
                .willThrow(daEx);

        assertThatThrownBy(() -> service.openImpersonation(
                        customerTenantId, staffUserId, null, "notfound@customer.local", "Support ticket"))
                .isInstanceOf(ImpersonationService.UserNotFoundException.class)
                .hasMessageContaining("Target user not found");
    }

    @Test
    @DisplayName("Opening impersonation maps P0002 database exception to TenantNotFoundException")
    @SuppressWarnings("unchecked")
    void openImpersonation_handlesTenantNotFound() {
        SQLException sqlEx = new SQLException("Tenant not found", "P0002");
        DataAccessException daEx = new TestDataAccessException("Database query failed", sqlEx);

        given(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        eq(customerTenantId),
                        eq(staffUserId),
                        any(),
                        any(),
                        any()))
                .willThrow(daEx);

        assertThatThrownBy(() ->
                        service.openImpersonation(customerTenantId, staffUserId, targetUserId, null, "Support ticket"))
                .isInstanceOf(ImpersonationService.TenantNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    @DisplayName("Opening bootstrap session on non-empty tenant maps P0005 to IllegalArgumentException")
    @SuppressWarnings("unchecked")
    void openImpersonation_handlesBootstrapForbiddenWhenUsersExist() {
        SQLException sqlEx =
                new SQLException("Bootstrap session not allowed: tenant has existing user accounts", "P0005");
        DataAccessException daEx = new TestDataAccessException("Database query failed", sqlEx);

        given(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        eq(customerTenantId),
                        eq(staffUserId),
                        any(),
                        any(),
                        any()))
                .willThrow(daEx);

        assertThatThrownBy(
                        () -> service.openImpersonation(customerTenantId, staffUserId, null, null, "First invitation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bootstrap session not allowed");
    }

    @Test
    @DisplayName("Closing impersonation calls database and throws SessionNotFoundException if unowned or not found")
    void closeImpersonation_callsDatabaseAndHandlesNotFound() {
        given(jdbcTemplate.queryForObject(
                        eq("SELECT core.close_impersonation(?, ?)"), eq(Boolean.class), eq(sessionId), eq(staffUserId)))
                .willReturn(true);

        service.closeImpersonation(sessionId, staffUserId);

        verify(jdbcTemplate)
                .queryForObject(
                        eq("SELECT core.close_impersonation(?, ?)"), eq(Boolean.class), eq(sessionId), eq(staffUserId));

        // When not found or unowned, returns false and throws
        given(jdbcTemplate.queryForObject(
                        eq("SELECT core.close_impersonation(?, ?)"), eq(Boolean.class), eq(sessionId), eq(staffUserId)))
                .willReturn(false);

        assertThatThrownBy(() -> service.closeImpersonation(sessionId, staffUserId))
                .isInstanceOf(ImpersonationService.SessionNotFoundException.class)
                .hasMessageContaining("not found or not owned by caller");
    }

    @Test
    @DisplayName("Resolving impersonation delegates to database and returns record")
    @SuppressWarnings("unchecked")
    void resolve_returnsResolvedSession() {
        ImpersonationResolver.ResolvedImpersonation resolved = new ImpersonationResolver.ResolvedImpersonation(
                sessionId,
                customerTenantId,
                staffUserId,
                targetUserId,
                "user@customer.local",
                Set.of("core.employee.read"));

        given(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(sessionId), eq(staffUserId)))
                .willReturn(Optional.of(resolved));

        Optional<ImpersonationResolver.ResolvedImpersonation> result = service.resolve(sessionId, staffUserId);

        assertThat(result).isPresent();
        assertThat(result.get().sessionId()).isEqualTo(sessionId);
        assertThat(result.get().tenantId()).isEqualTo(customerTenantId);
        assertThat(result.get().actionCodes()).containsExactly("core.employee.read");
    }

    private static class TestDataAccessException extends DataAccessException {
        TestDataAccessException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
