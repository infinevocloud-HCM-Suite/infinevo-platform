package com.infinevo.payroll.fbp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link EmployeeFbpComponent} entity (W-27.2).
 */
public interface EmployeeFbpComponentRepository extends JpaRepository<EmployeeFbpComponent, UUID> {

    List<EmployeeFbpComponent> findAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);

    List<EmployeeFbpComponent> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<EmployeeFbpComponent> findByTenantIdAndCtcStructureIdAndEarningId(
            UUID tenantId, UUID ctcStructureId, UUID earningId);

    Optional<EmployeeFbpComponent> findByTenantIdAndCtcStructureIdAndReimbursementId(
            UUID tenantId, UUID ctcStructureId, UUID reimbursementId);

    @Modifying
    @Query("DELETE FROM EmployeeFbpComponent c WHERE c.tenantId = :tenantId AND c.ctcStructureId = :ctcStructureId")
    void deleteAllByTenantIdAndCtcStructureId(
            @Param("tenantId") UUID tenantId, @Param("ctcStructureId") UUID ctcStructureId);
}
