package com.infinevo.hrms.performance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for {@link PerformanceCycle}.
 */
@Transactional(readOnly = true)
public interface PerformanceCycleRepository extends JpaRepository<PerformanceCycle, UUID> {

    Optional<PerformanceCycle> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PerformanceCycle> findByTenantIdOrderByPeriodStartDescPeriodEndDesc(UUID tenantId);

    List<PerformanceCycle> findByTenantIdAndStatus(UUID tenantId, PerformanceCycleStatus status);
}
