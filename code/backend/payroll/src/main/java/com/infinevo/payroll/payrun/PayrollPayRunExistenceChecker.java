package com.infinevo.payroll.payrun;

import com.infinevo.core.tenant.PayRunExistenceChecker;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PayRunExistenceChecker} for the payroll module (W-116).
 * Checks whether a tenant has any pay run whose status is not CANCELLED.
 */
@Component
public class PayrollPayRunExistenceChecker implements PayRunExistenceChecker {

    private final JdbcTemplate jdbcTemplate;

    public PayrollPayRunExistenceChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public boolean hasProcessedPayRun(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payroll.payrun WHERE tenant_id = ? AND status <> 'CANCELLED'",
                Integer.class,
                tenantId);
        return count != null && count > 0;
    }
}
