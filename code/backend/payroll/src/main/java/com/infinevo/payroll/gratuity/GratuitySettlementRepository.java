package com.infinevo.payroll.gratuity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link GratuitySettlement} with explicit tenant scoping (W-91).
 */
public interface GratuitySettlementRepository extends JpaRepository<GratuitySettlement, UUID> {

    Optional<GratuitySettlement> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndEmployeeIdAndStatusNot(UUID tenantId, UUID employeeId, GratuitySettlementStatus status);
}
