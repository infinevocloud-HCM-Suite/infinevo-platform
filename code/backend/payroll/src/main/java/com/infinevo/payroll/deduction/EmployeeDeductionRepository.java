package com.infinevo.payroll.deduction;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the filter. */
public interface EmployeeDeductionRepository
        extends JpaRepository<EmployeeDeduction, UUID>, JpaSpecificationExecutor<EmployeeDeduction> {

    Optional<EmployeeDeduction> findByTenantIdAndId(UUID tenantId, UUID id);

    /** An employee's own rows, newest period first, reversed ones included (§4, {@code GET /me}). */
    List<EmployeeDeduction> findByTenantIdAndEmployeeIdOrderByPeriodDescCreatedAtDesc(UUID tenantId, UUID employeeId);

    /** {@code SELECT … FOR UPDATE}: two reversals of one row serialise, and the second sees {@code REVERSED}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM EmployeeDeduction d WHERE d.id = :id AND d.tenantId = :tenantId")
    Optional<EmployeeDeduction> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
