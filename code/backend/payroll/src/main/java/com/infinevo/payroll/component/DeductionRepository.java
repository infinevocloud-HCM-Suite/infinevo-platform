package com.infinevo.payroll.component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Deduction} entities (W-26.1).
 */
public interface DeductionRepository extends JpaRepository<Deduction, UUID> {

    Optional<Deduction> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Deduction> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    List<Deduction> findAllByTenantIdAndActiveAndDeletedFalse(UUID tenantId, boolean active);

    Optional<Deduction> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);
}
