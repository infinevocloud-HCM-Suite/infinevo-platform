package com.infinevo.core.leave;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional helper for bulk leave import operations (W-16.4b, spec section 9 risk 1).
 *
 * <p>Each method executes in an independent {@code REQUIRES_NEW} transaction so that
 * an individual allocation failure (e.g. duplicate constraint violation) does not roll back
 * previously committed allocations or abort the remaining imports.
 */
@Component
public class LeaveImportAllocationHelper {

    private final LeaveAllocationService leaveAllocationService;
    private final LeaveImportLogRepository importLogRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public void setJdbcTemplate(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LeaveImportAllocationHelper(
            LeaveAllocationService leaveAllocationService, LeaveImportLogRepository importLogRepository) {
        this.leaveAllocationService =
                Objects.requireNonNull(leaveAllocationService, "leaveAllocationService must not be null");
        this.importLogRepository = Objects.requireNonNull(importLogRepository, "importLogRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeaveImportLog saveLog(LeaveImportLog log) {
        return importLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeaveAllocationResponse createOneAllocation(UUID tenantId, LeaveAllocationRequest request) {
        return leaveAllocationService.createAllocation(tenantId, request);
    }

    @Transactional(readOnly = true)
    public int getTenantLeaveYearStartMonth(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not available to read tenant metadata");
        }
        java.util.List<Integer> results = jdbcTemplate.query(
                "SELECT leave_year_start_month FROM core.tenant WHERE tenant_id = ?",
                (rs, rowNum) -> {
                    int val = rs.getInt(1);
                    return rs.wasNull() ? null : val;
                },
                tenantId);
        if (results.isEmpty() || results.get(0) == null) {
            throw new IllegalStateException("Tenant " + tenantId + " not found or leave_year_start_month is missing");
        }
        int month = results.get(0);
        if (month < 1 || month > 12) {
            throw new IllegalStateException("Invalid leave_year_start_month for tenant " + tenantId + ": " + month);
        }
        return month;
    }
}
