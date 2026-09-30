package com.infinevo.payroll.component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Benefit} entities (W-26.1).
 */
public interface BenefitRepository extends JpaRepository<Benefit, UUID> {

    Optional<Benefit> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Benefit> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    /** The benefits flagged {@code is_pro_rata}: one query per pay run for loss of pay (W-29.3 §4). */
    List<Benefit> findAllByTenantIdAndProRataTrueAndDeletedFalse(UUID tenantId);

    List<Benefit> findAllByTenantIdAndActiveAndDeletedFalse(UUID tenantId, boolean active);

    Optional<Benefit> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);
}
