package com.infinevo.payroll.taxdeclaration.summary;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvTaxSummary} (W-32.4).
 */
@Repository
public interface EmployeeInvTaxSummaryRepository extends JpaRepository<EmployeeInvTaxSummary, UUID> {

    Optional<EmployeeInvTaxSummary> findByTenantIdAndDeclarationIdAndRegime(
            UUID tenantId, UUID declarationId, String regime);

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value =
                    """
            INSERT INTO payroll.employee_inv_tax_summary (id, tenant_id, declaration_id, regime, created_at, updated_at, created_by, updated_by)
            VALUES (gen_random_uuid(), :tenantId, :declarationId, :regime, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system')
            ON CONFLICT (tenant_id, declaration_id, regime) DO NOTHING
            """,
            nativeQuery = true)
    int insertIgnoreConflict(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("declarationId") UUID declarationId,
            @org.springframework.data.repository.query.Param("regime") String regime);
}
