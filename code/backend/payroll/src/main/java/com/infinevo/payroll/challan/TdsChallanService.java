package com.infinevo.payroll.challan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** TDS challans: record, allocate to pay run TDS, mark filed (W-89.1). */
public interface TdsChallanService {

    TdsChallanResponse create(TdsChallanRequest request);

    TdsChallanResponse update(UUID id, TdsChallanRequest request);

    Optional<TdsChallanResponse> findById(UUID id);

    List<TdsChallanResponse> list(String financialYear, String quarter);

    void delete(UUID id);

    TdsChallanResponse allocate(UUID challanId, ChallanAllocationRequest request);

    List<TdsChallanAllocationResponse> listAllocations(UUID challanId);

    /** Everyone with TDS on an approved or paid run of the challan's quarter, and how much is still uncovered. */
    List<AllocationCandidate> candidates(UUID challanId);

    /** Marks a quarter's return filed, with its token number; its challans and allocations lock. */
    TdsReturnResponse markFiled(String financialYear, String quarter, TdsReturnRequest request);

    /** Unmarks a filed quarter, for a correction. */
    void reopen(String financialYear, String quarter);

    /** The filed quarters of a year. */
    List<TdsReturnResponse> returns(String financialYear);
}
