package com.infinevo.payroll.taxdeclaration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link IncomeTaxDeclarationWindow} (W-32.1).
 *
 * <p>Every finder is tenant-scoped (DEBT-022, CONVENTIONS.md Rule 7).
 */
@Repository
public interface IncomeTaxDeclarationWindowRepository extends JpaRepository<IncomeTaxDeclarationWindow, UUID> {

    Optional<IncomeTaxDeclarationWindow> findByTenantIdAndFinancialYear(UUID tenantId, String financialYear);
}
