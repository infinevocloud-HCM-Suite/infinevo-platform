package com.infinevo.payroll.component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Earning} entities (W-26.1).
 */
public interface EarningRepository extends JpaRepository<Earning, UUID> {

    Optional<Earning> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Earning> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    /** The earnings flagged {@code is_pro_rata}: one query per pay run for loss of pay (W-29.3 §4). */
    List<Earning> findAllByTenantIdAndProRataTrueAndDeletedFalse(UUID tenantId);

    List<Earning> findAllByTenantIdAndActiveAndDeletedFalse(UUID tenantId, boolean active);

    List<Earning> findAllByTenantIdAndActiveAndDeletedFalseAndFbpComponentTrue(UUID tenantId, boolean active);

    Optional<Earning> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);

    boolean existsByTenantIdAndParentEarningIdAndDeletedFalse(UUID tenantId, UUID parentEarningId);

    boolean existsByTenantIdAndDeletedFalse(UUID tenantId);
}
