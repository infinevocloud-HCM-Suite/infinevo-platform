package com.infinevo.core.tenant;

import com.infinevo.shared.tenant.PlatformTenant;
import java.sql.Array;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying tenant overviews across Row-Level Security for platform staff (W-65.1).
 *
 * <p>Every method verifies that the bound tenant is the Infinevo platform tenant via
 * {@link PlatformTenant#requirePlatformTenant()} before querying the database.
 */
@Service
public class TenantQueryService {

    private static final RowMapper<TenantOverview> ROW_MAPPER = (rs, rowNum) -> {
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        String name = rs.getString("name");
        String countryCode = rs.getString("country_code");
        String timezone = rs.getString("timezone");
        String status = rs.getString("status");

        List<String> modules = List.of();
        Array array = rs.getArray("modules");
        if (array != null) {
            Object arrObj = array.getArray();
            if (arrObj instanceof String[] strings) {
                modules = Arrays.asList(strings);
            }
        }

        Timestamp ts = rs.getTimestamp("created_at");
        Instant createdAt = ts != null ? ts.toInstant() : null;

        Date date = rs.getDate("current_period_end");
        LocalDate currentPeriodEnd = date != null ? date.toLocalDate() : null;

        long userCount = rs.getLong("user_count");

        // D-42: V159 adds the administrator invitation's email and status.
        TenantOverview.AdminInvitationStatus adminStatus =
                TenantOverview.AdminInvitationStatus.of(rs.getString("admin_invitation_status"));
        TenantOverview.AdminInvitation adminInvitation = adminStatus == TenantOverview.AdminInvitationStatus.NONE
                ? TenantOverview.AdminInvitation.NONE
                : new TenantOverview.AdminInvitation(rs.getString("admin_invitation_email"), adminStatus);

        return new TenantOverview(
                tenantId,
                name,
                countryCode,
                timezone,
                status,
                modules,
                createdAt,
                currentPeriodEnd,
                userCount,
                adminInvitation);
    };

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTenant platformTenant;

    public TenantQueryService(JdbcTemplate jdbcTemplate, PlatformTenant platformTenant) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.platformTenant = Objects.requireNonNull(platformTenant, "platformTenant must not be null");
    }

    /**
     * Lists all tenants across the platform.
     *
     * @return list of tenant overviews ordered by name
     */
    @Transactional(readOnly = true)
    public List<TenantOverview> list() {
        platformTenant.requirePlatformTenant();
        return jdbcTemplate.query("SELECT * FROM core.list_tenants()", ROW_MAPPER);
    }

    /**
     * Retrieves the overview of a single tenant.
     *
     * @param id tenant UUID
     * @return the tenant overview
     * @throws TenantNotFoundException if the tenant does not exist
     */
    @Transactional(readOnly = true)
    public TenantOverview getOverview(UUID id) {
        Objects.requireNonNull(id, "tenantId must not be null");
        platformTenant.requirePlatformTenant();
        List<TenantOverview> results = jdbcTemplate.query("SELECT * FROM core.get_tenant_overview(?)", ROW_MAPPER, id);
        if (results.isEmpty()) {
            throw new TenantNotFoundException(id);
        }
        return results.get(0);
    }
}
