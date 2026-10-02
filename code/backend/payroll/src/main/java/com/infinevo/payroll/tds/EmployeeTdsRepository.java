package com.infinevo.payroll.tds;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link EmployeeTds} (W-36.1 §4).
 *
 * <p>Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the
 * filter.
 */
public interface EmployeeTdsRepository extends JpaRepository<EmployeeTds, UUID> {

    @Query(
            """
            SELECT e FROM EmployeeTds e
            WHERE e.tenantId = :tenantId
              AND e.employeeId = :employeeId
              AND e.financialYear = :financialYear
              AND e.isActive = true
            """)
    Optional<EmployeeTds> findActive(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    /** As {@link #findActive}, holding a row lock until the transaction ends (§4, concurrent PUTs). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT e FROM EmployeeTds e
            WHERE e.tenantId = :tenantId
              AND e.employeeId = :employeeId
              AND e.financialYear = :financialYear
              AND e.isActive = true
            """)
    Optional<EmployeeTds> findActiveForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    List<EmployeeTds> findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId, String financialYear);
}
