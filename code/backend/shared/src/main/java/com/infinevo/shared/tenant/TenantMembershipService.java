package com.infinevo.shared.tenant;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validates user-tenant memberships against PostgreSQL table {@code core.user_tenant}.
 */
public class TenantMembershipService {

    private static final Logger log = LoggerFactory.getLogger(TenantMembershipService.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public static class TenantMembershipAccessException extends RuntimeException {
        public TenantMembershipAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public TenantMembershipService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Checks if the user is a member of the specified target tenant.
     *
     * <p>Solves the RLS bootstrap paradox by opening a dedicated transaction and executing
     * {@code SELECT set_config('app.current_tenant_id', ?, true)} (D-57) on the connection
     * prior to checking membership, so PostgreSQL RLS permits matching rows.
     */
    public boolean isUserMemberOfTenant(UUID userId, UUID tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                // Bind target tenant to session for the duration of the membership check
                try (var stmt = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                    stmt.setString(1, tenantId.toString());
                    stmt.execute();
                }
                // Check if user_tenant contains a matching record
                try (var stmt =
                        conn.prepareStatement("SELECT 1 FROM core.user_tenant WHERE user_id = ? AND tenant_id = ?")) {
                    stmt.setObject(1, userId);
                    stmt.setObject(2, tenantId);
                    try (var rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                }
            } finally {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            log.error(
                    "Database infrastructure error during membership verification for user {} and tenant {}",
                    userId,
                    tenantId,
                    e);
            throw new TenantMembershipAccessException(
                    "Database infrastructure failure during tenant membership lookup", e);
        }
    }

    /**
     * Retrieves all tenant IDs the user belongs to by calling PostgreSQL function
     * {@code core.get_user_tenants(user_id)} (marked {@code SECURITY DEFINER}).
     */
    public List<UUID> getUserTenants(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT tenant_id FROM core.get_user_tenants(?)",
                    (rs, rowNum) -> rs.getObject("tenant_id", UUID.class),
                    userId);
        } catch (Exception e) {
            log.error("Database infrastructure error during tenant retrieval for user {}", userId, e);
            throw new TenantMembershipAccessException(
                    "Database infrastructure failure during user tenant retrieval", e);
        }
    }
}
