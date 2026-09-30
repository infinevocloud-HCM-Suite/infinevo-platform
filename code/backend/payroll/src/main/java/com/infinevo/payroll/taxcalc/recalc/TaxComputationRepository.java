package com.infinevo.payroll.taxcalc.recalc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TaxComputationRecord} audit history (W-33.3).
 */
@Repository
public interface TaxComputationRepository extends JpaRepository<TaxComputationRecord, UUID> {

    List<TaxComputationRecord> findByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
            UUID tenantId, UUID employeeId, String financialYear);

    List<TaxComputationRecord> findByTenantIdAndDeclarationIdOrderByComputedAtDesc(UUID tenantId, UUID declarationId);
}
