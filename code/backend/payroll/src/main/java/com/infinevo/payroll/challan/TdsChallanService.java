package com.infinevo.payroll.challan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for TDS challan lifecycle and pay run allocations (W-89.1).
 */
public interface TdsChallanService {

    TdsChallanResponse create(TdsChallanRequest request);

    TdsChallanResponse update(UUID id, TdsChallanRequest request);

    Optional<TdsChallanResponse> findById(UUID id);

    List<TdsChallanResponse> list(String financialYear, String quarter);

    void delete(UUID id);

    TdsChallanResponse allocate(UUID challanId, ChallanAllocationRequest request);

    List<TdsChallanAllocation> listAllocations(UUID challanId);
}
