package com.infinevo.payroll.tds;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link EmployeeTds} (W-36.1 §4).
 * Every finder takes {@code tenantId} (DEBT-022).
 */
public interface EmployeeTdsRepository extends JpaRepository<EmployeeTds, UUID> {

    @Query("SELECT e FROM EmployeeTds e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId "
            + "AND e.financialYear = :financialYear AND e.isActive = true")
    Optional<EmployeeTds> findActive(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    List<EmployeeTds> findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId, String financialYear);
}
