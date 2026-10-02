package com.infinevo.shared.impersonation;

import com.infinevo.shared.tenant.PlatformTenant;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing impersonation sessions by calling database functions
 * {@code core.open_impersonation}, {@code core.resolve_impersonation}, and
 * {@code core.close_impersonation} (W-65.2).
 */
public class ImpersonationService implements ImpersonationResolver {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationService.class);

    private final JdbcTemplate jdbcTemplate;

    public ImpersonationService(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public ImpersonationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** Details of a newly opened impersonation session. */
    public record OpenedImpersonation(
            UUID sessionId, UUID tenantId, UUID userAccountId, String userEmail, Instant expiresAt) {

        public boolean isBootstrap() {
            return userAccountId == null;
        }
    }

    /** Thrown when target user is not found in the customer tenant. */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when target tenant does not exist. */
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when impersonation session is not found or not owned by caller. */
    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Opens an impersonation session by calling {@code core.open_impersonation}.
     *
     * <p>Transactional because the request that calls it has the staff member's tenant bound: the datasource
     * binds a tenant per transaction and refuses a connection in auto-commit mode, so without one every call
     * is a server error. {@code resolve} runs in the filter before any tenant is bound and needs none.
     *
     * @param targetTenant        the customer tenant to act inside
     * @param platformUserId      the staff user Keycloak subject
     * @param targetUserAccountId optional target user account ID (or null)
     * @param targetEmail         optional target user email (or null)
     * @param reason              justification reason (required, &lt;= 200 chars)
     * @return the opened session record
     */
    @Transactional
    public OpenedImpersonation openImpersonation(
            UUID targetTenant, UUID platformUserId, UUID targetUserAccountId, String targetEmail, String reason) {
        if (targetTenant == null) {
            throw new IllegalArgumentException("Target tenant must not be null");
        }
        if (PlatformTenant.DEFAULT_PLATFORM_TENANT_ID.equals(targetTenant)) {
            throw new IllegalArgumentException("Cannot impersonate platform tenant");
        }
        if (platformUserId == null) {
            throw new IllegalArgumentException("Platform user ID must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 200) {
            throw new IllegalArgumentException("Reason must not exceed 200 characters");
        }
        String trimmedEmail = targetEmail != null && !targetEmail.isBlank() ? targetEmail.trim() : null;

        try {
            return jdbcTemplate.query(
                    "SELECT session_id, tenant_id, user_account_id, user_email, expires_at "
                            + "FROM core.open_impersonation(?, ?, ?, ?, ?)",
                    rs -> {
                        if (rs.next()) {
                            UUID sessionId = rs.getObject("session_id", UUID.class);
                            UUID tenantId = rs.getObject("tenant_id", UUID.class);
                            UUID accountId = rs.getObject("user_account_id", UUID.class);
                            String email = rs.getString("user_email");
                            Timestamp expiresTimestamp = rs.getTimestamp("expires_at");
                            Instant expiresAt = expiresTimestamp != null ? expiresTimestamp.toInstant() : null;
                            return new OpenedImpersonation(sessionId, tenantId, accountId, email, expiresAt);
                        }
                        throw new IllegalStateException("core.open_impersonation did not return a row");
                    },
                    targetTenant,
                    platformUserId,
                    targetUserAccountId,
                    trimmedEmail,
                    trimmedReason);
        } catch (DataAccessException e) {
            handleDatabaseException(e, targetTenant, targetUserAccountId, trimmedEmail);
            throw e;
        }
    }

    /**
     * Closes an impersonation session by calling {@code core.close_impersonation}.
     *
     * @param sessionId      the session ID
     * @param platformUserId the staff user Keycloak subject (must match the opener)
     */
    @Transactional
    public void closeImpersonation(UUID sessionId, UUID platformUserId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID must not be null");
        }
        if (platformUserId == null) {
            throw new IllegalArgumentException("Platform user ID must not be null");
        }
        try {
            Boolean closed = jdbcTemplate.queryForObject(
                    "SELECT core.close_impersonation(?, ?)", Boolean.class, sessionId, platformUserId);
            if (!Boolean.TRUE.equals(closed)) {
                throw new SessionNotFoundException(
                        "Impersonation session " + sessionId + " not found or not owned by caller");
            }
        } catch (DataAccessException e) {
            log.error("Database error closing impersonation session {} for staff {}", sessionId, platformUserId, e);
            throw new RuntimeException("Database error closing impersonation session", e);
        }
    }

    @Override
    public Optional<ResolvedImpersonation> resolve(UUID sessionId, UUID platformUserId) {
        if (sessionId == null || platformUserId == null) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT session_id, tenant_id, platform_user_id, target_user_account_id, target_email, action_codes "
                            + "FROM core.resolve_impersonation(?, ?)",
                    rs -> {
                        if (rs.next()) {
                            UUID sId = rs.getObject("session_id", UUID.class);
                            UUID tId = rs.getObject("tenant_id", UUID.class);
                            UUID pUserId = rs.getObject("platform_user_id", UUID.class);
                            UUID targetAccountId = rs.getObject("target_user_account_id", UUID.class);
                            String targetEmail = rs.getString("target_email");
                            Array sqlArray = rs.getArray("action_codes");
                            Set<String> actionCodes = Set.of();
                            if (sqlArray != null) {
                                Object arrayObj = sqlArray.getArray();
                                if (arrayObj instanceof String[] strArr) {
                                    actionCodes = Set.copyOf(Arrays.asList(strArr));
                                }
                            }
                            return Optional.of(new ResolvedImpersonation(
                                    sId, tId, pUserId, targetAccountId, targetEmail, actionCodes));
                        }
                        return Optional.empty();
                    },
                    sessionId,
                    platformUserId);
        } catch (Exception e) {
            log.error(
                    "Database infrastructure error resolving impersonation session {} for staff {}",
                    sessionId,
                    platformUserId,
                    e);
            throw new RuntimeException("Database error resolving impersonation session", e);
        }
    }

    private void handleDatabaseException(
            DataAccessException e, UUID targetTenant, UUID targetUserAccountId, String targetEmail) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        if (cause instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if ("P0001".equals(sqlState)) {
                throw new IllegalArgumentException("Cannot impersonate platform tenant");
            }
            if ("P0002".equals(sqlState)) {
                throw new TenantNotFoundException("Tenant not found: " + targetTenant);
            }
            if ("P0003".equals(sqlState)) {
                throw new IllegalArgumentException(message);
            }
            if ("P0004".equals(sqlState)) {
                throw new UserNotFoundException(
                        "Target user not found: " + (targetUserAccountId != null ? targetUserAccountId : targetEmail));
            }
            if ("P0005".equals(sqlState)) {
                throw new IllegalArgumentException("Bootstrap session not allowed: tenant has existing user accounts");
            }
        }
        if (message != null) {
            if (message.contains("Cannot impersonate platform tenant")) {
                throw new IllegalArgumentException("Cannot impersonate platform tenant");
            }
            if (message.contains("Tenant not found")) {
                throw new TenantNotFoundException("Tenant not found: " + targetTenant);
            }
            if (message.contains("Reason")) {
                throw new IllegalArgumentException(message);
            }
            if (message.contains("Target user not found")) {
                throw new UserNotFoundException(
                        "Target user not found: " + (targetUserAccountId != null ? targetUserAccountId : targetEmail));
            }
            if (message.contains("Bootstrap session not allowed")) {
                throw new IllegalArgumentException("Bootstrap session not allowed: tenant has existing user accounts");
            }
        }
    }
}
