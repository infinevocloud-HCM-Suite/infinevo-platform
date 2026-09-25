package com.infinevo.core.tenant;

import com.infinevo.shared.entitlement.PlatformModule;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TenantService} (W-12.1).
 *
 * <p>Provisions a new tenant through the PostgreSQL {@code SECURITY DEFINER} function
 * {@code core.provision_tenant}, which atomically writes the tenant, subscription, and module records.
 */
@Service
public class TenantServiceImpl implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    private static final String DEFAULT_COUNTRY_CODE = "IN";
    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    private static final short DEFAULT_LEAVE_YEAR_START_MONTH = 4;

    private final JdbcTemplate jdbcTemplate;

    public TenantServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    @Transactional
    public TenantResponse provisionTenant(TenantRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. Validate name
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required and must not be blank");
        }
        String name = request.name().trim();

        // 2. Validate country_code
        String rawCountry = request.countryCode();
        String countryCode = DEFAULT_COUNTRY_CODE;
        if (rawCountry != null && !rawCountry.isBlank()) {
            String trimmed = rawCountry.trim();
            if (trimmed.length() != 2) {
                throw new IllegalArgumentException("country_code must be a 2-character ISO 3166-1 alpha-2 code");
            }
            countryCode = trimmed.toUpperCase();
        }

        // 3. Validate timezone
        String rawTimezone = request.timeZone();
        String timezone = DEFAULT_TIMEZONE;
        if (rawTimezone != null && !rawTimezone.isBlank()) {
            String trimmed = rawTimezone.trim();
            try {
                ZoneId.of(trimmed);
                timezone = trimmed;
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Invalid timezone: " + trimmed, e);
            }
        }

        // 4. Validate leave_year_start_month
        Short rawMonth = request.leaveYearStartMonth();
        short leaveYearStartMonth = DEFAULT_LEAVE_YEAR_START_MONTH;
        if (rawMonth != null) {
            if (rawMonth < 1 || rawMonth > 12) {
                throw new IllegalArgumentException("leave_year_start_month must be between 1 and 12");
            }
            leaveYearStartMonth = rawMonth;
        }

        // 5. Modules
        Set<PlatformModule> modules = request.modules() != null ? request.modules() : Set.of();
        String[] moduleNames = modules.stream().map(Enum::name).toArray(String[]::new);

        final String finalCountryCode = countryCode;
        final String finalTimezone = timezone;
        final short finalMonth = leaveYearStartMonth;

        UUID tenantId = jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT core.provision_tenant(?, ?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, finalCountryCode);
                ps.setString(3, finalTimezone);
                ps.setShort(4, finalMonth);
                Array array = conn.createArrayOf("text", moduleNames);
                ps.setArray(5, array);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject(1, UUID.class);
                    }
                    throw new IllegalStateException("provision_tenant function did not return a tenant id");
                }
            }
        });

        log.info("Provisioned tenant {} ({}) with modules {}", tenantId, name, modules);

        return new TenantResponse(tenantId, tenantId, name, finalCountryCode, finalTimezone, finalMonth, modules);
    }
}
