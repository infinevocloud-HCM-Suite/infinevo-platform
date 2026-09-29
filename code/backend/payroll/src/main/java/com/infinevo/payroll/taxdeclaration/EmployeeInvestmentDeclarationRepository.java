package com.infinevo.payroll.taxdeclaration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvestmentDeclaration} (W-32.1).
 *
 * <p>Every finder is tenant-scoped (DEBT-022, CONVENTIONS.md Rule 7).
 */
@Repository
public interface EmployeeInvestmentDeclarationRepository extends JpaRepository<EmployeeInvestmentDeclaration, UUID> {

    Optional<EmployeeInvestmentDeclaration> findByTenantIdAndEmployeeIdAndFinancialYear(
            UUID tenantId, UUID employeeId, String financialYear);

    Optional<EmployeeInvestmentDeclaration> findByTenantIdAndId(UUID tenantId, UUID id);

    List<EmployeeInvestmentDeclaration> findByTenantIdAndFinancialYearAndStatus(
            UUID tenantId, String financialYear, DeclarationStatus status);
}
