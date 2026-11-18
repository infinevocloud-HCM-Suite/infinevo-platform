package com.infinevo.hrms.performance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for {@link PerformanceReview}.
 */
@Transactional(readOnly = true)
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    Optional<PerformanceReview> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PerformanceReview> findByTenantIdAndCycleIdAndEmployeeId(UUID tenantId, UUID cycleId, UUID employeeId);

    List<PerformanceReview> findByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    List<PerformanceReview> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    List<PerformanceReview> findByTenantIdAndManagerIdOrderByCreatedAtDesc(UUID tenantId, UUID managerId);

    List<PerformanceReview> findByTenantIdAndManagerIdAndStatus(
            UUID tenantId, UUID managerId, PerformanceReviewStatus status);

    long countByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    long countByTenantIdAndCycleIdAndStatus(UUID tenantId, UUID cycleId, PerformanceReviewStatus status);
}
