package com.infinevo.core.tenant;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Provides tenant-aware clock operations based on the bound tenant's configured timezone (W-40.2).
 */
@Service
public class TenantClock {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public TenantClock(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    public TenantClock(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Resolves the configured {@link ZoneId} for the currently bound tenant.
     * Falls back to {@code Asia/Kolkata} if timezone is null, blank, or invalid.
     */
    public ZoneId zone() {
        UUID tenantId = TenantContext.require();
        List<String> results = jdbcTemplate.query(
                "SELECT timezone FROM core.tenant WHERE tenant_id = ?",
                (rs, rowNum) -> rs.getString("timezone"),
                tenantId);
        if (results.isEmpty() || results.get(0) == null || results.get(0).isBlank()) {
            return DEFAULT_ZONE;
        }
        String tz = results.get(0).trim();
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }

    /**
     * Returns today's date in the bound tenant's timezone.
     */
    public LocalDate today() {
        return dateOf(clock.instant());
    }

    /**
     * Converts an {@link Instant} to a {@link LocalDate} in the bound tenant's timezone.
     */
    public LocalDate dateOf(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return instant.atZone(zone()).toLocalDate();
    }
}
