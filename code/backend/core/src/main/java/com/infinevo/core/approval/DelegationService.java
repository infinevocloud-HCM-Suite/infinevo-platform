package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for approval delegation management (W-15.3).
 */
public interface DelegationService {

    DelegationResponse createDelegation(UUID tenantId, UUID callerEmployeeId, DelegationCreateRequest request);

    List<DelegationResponse> getDelegations(UUID tenantId, UUID employeeId, LocalDate activeOn);

    void deleteDelegation(UUID tenantId, UUID delegationId, UUID callerEmployeeId);

    Optional<UUID> resolveDelegate(UUID tenantId, UUID delegatorId, ApprovalFlowType flowType, LocalDate asOf);
}
