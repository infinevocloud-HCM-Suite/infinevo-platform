package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WfhAllocationService {

    WfhAllocationResponse createAllocation(UUID tenantId, CreateWfhAllocationRequest request);

    List<WfhAllocationResponse> listAllocations(UUID tenantId, UUID employeeId, String year);

    BigDecimal calculateProRatedDays(BigDecimal annualQuota, LocalDate joiningDate, LocalDate yearEndDate);

    WfhAllocationResponse allocateForNewJoiner(UUID tenantId, UUID employeeId, LocalDate joiningDate);
}
