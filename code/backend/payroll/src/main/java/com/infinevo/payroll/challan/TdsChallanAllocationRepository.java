package com.infinevo.payroll.challan;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TdsChallanAllocation} (W-89.1).
 */
@Repository
public interface TdsChallanAllocationRepository extends JpaRepository<TdsChallanAllocation, UUID> {

    List<TdsChallanAllocation> findByTenantIdAndChallanId(UUID tenantId, UUID challanId);

    List<TdsChallanAllocation> findByTenantIdAndChallanIdIn(UUID tenantId, Collection<UUID> challanIds);

    List<TdsChallanAllocation> findByTenantIdAndPayRunId(UUID tenantId, UUID payRunId);

    void deleteByTenantIdAndChallanId(UUID tenantId, UUID challanId);
}
