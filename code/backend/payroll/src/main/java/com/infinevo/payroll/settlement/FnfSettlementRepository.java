package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link FnfSettlement} with multi-tenant isolation.
 */
@Repository
public interface FnfSettlementRepository extends JpaRepository<FnfSettlement, UUID> {

    Optional<FnfSettlement> findByTenantIdAndId(UUID tenantId, UUID id);

    List<FnfSettlement> findByTenantId(UUID tenantId);

    List<FnfSettlement> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<FnfSettlement> findByTenantIdAndEmployeeIdAndStatusNot(UUID tenantId, UUID employeeId, String status);
}
