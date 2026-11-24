package com.infinevo.payroll.salary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link EmployeeStatutoryProfile} entities (W-26.2).
 */
public interface EmployeeStatutoryProfileRepository extends JpaRepository<EmployeeStatutoryProfile, UUID> {

    Optional<EmployeeStatutoryProfile> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<EmployeeStatutoryProfile> findAllByTenantIdAndEmployeeIdIn(UUID tenantId, Collection<UUID> employeeIds);

    boolean existsByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    /** D-145: employees of the tenant whose profile says TDS is not applicable. */
    @Query(
            "SELECT p.employeeId FROM EmployeeStatutoryProfile p WHERE p.tenantId = :tenantId AND p.tdsApplicable = false")
    List<UUID> findNotTdsApplicableEmployeeIds(@Param("tenantId") UUID tenantId);
}
