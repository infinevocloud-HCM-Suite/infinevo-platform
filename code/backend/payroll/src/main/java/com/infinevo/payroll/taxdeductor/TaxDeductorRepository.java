package com.infinevo.payroll.taxdeductor;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tax deductor entities scoped by tenant (W-36.3, DEBT-022).
 */
@Repository
public interface TaxDeductorRepository extends JpaRepository<TaxDeductor, UUID> {

    /**
     * Looks up the tax deductor record for the given tenant.
     *
     * @param tenantId the tenant ID
     * @return optional containing the deductor if found
     */
    Optional<TaxDeductor> findByTenantId(UUID tenantId);
}
