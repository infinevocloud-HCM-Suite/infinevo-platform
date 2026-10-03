package com.infinevo.payroll.form16;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Form 16 Part A certificate records (W-36.5 §4).
 */
@Repository
@Transactional(readOnly = true)
public interface Form16PartARepository extends JpaRepository<Form16PartA, UUID> {

    Optional<Form16PartA> findByTenantIdAndEmployeeIdAndFinancialYearAndIsActiveTrue(
            UUID tenantId, UUID employeeId, String financialYear);

    default Optional<Form16PartA> findActive(UUID tenantId, UUID employeeId, String financialYear) {
        return findByTenantIdAndEmployeeIdAndFinancialYearAndIsActiveTrue(tenantId, employeeId, financialYear);
    }

    List<Form16PartA> findByTenantIdAndFinancialYearAndIsActiveTrue(UUID tenantId, String financialYear);

    default List<Form16PartA> findActiveByTenantIdAndFinancialYear(UUID tenantId, String financialYear) {
        return findByTenantIdAndFinancialYearAndIsActiveTrue(tenantId, financialYear);
    }

    List<Form16PartA> findByTenantIdAndEmployeeIdAndFinancialYear(UUID tenantId, UUID employeeId, String financialYear);
}
