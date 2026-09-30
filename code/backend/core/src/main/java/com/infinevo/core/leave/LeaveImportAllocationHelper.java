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
}
