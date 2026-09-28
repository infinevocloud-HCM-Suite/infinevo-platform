package com.infinevo.payroll.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link CtcStructure} entities (W-26.2).
 */
public interface CtcStructureRepository extends JpaRepository<CtcStructure, UUID> {

    Optional<CtcStructure> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CtcStructure>
            findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    UUID tenantId, UUID employeeId, LocalDate effectiveFrom);

    List<CtcStructure> findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(UUID tenantId, UUID employeeId);

    boolean existsByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    boolean existsByTenantIdAndEmployeeIdAndEffectiveFrom(UUID tenantId, UUID employeeId, LocalDate effectiveFrom);

    boolean existsByTenantIdAndEmployeeIdAndEffectiveFromAndIdNot(
            UUID tenantId, UUID employeeId, LocalDate effectiveFrom, UUID id);
}
