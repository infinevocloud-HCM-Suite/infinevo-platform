package com.infinevo.core.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ApprovalStep} (W-15.2).
 */
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    Optional<ApprovalStep> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ApprovalStep> findByTenantIdAndInstanceIdOrderByStepIndexAsc(UUID tenantId, UUID instanceId);

    List<ApprovalStep> findByTenantIdAndInstanceIdAndStepIndex(UUID tenantId, UUID instanceId, int stepIndex);

    List<ApprovalStep> findByTenantIdAndInstanceIdAndDecisionIsNull(UUID tenantId, UUID instanceId);

    Page<ApprovalStep> findByTenantIdAndAssigneeEmployeeIdAndDecisionIsNullOrderByCreatedAtAsc(
            UUID tenantId, UUID assigneeEmployeeId, Pageable pageable);

    @Query("SELECT s FROM ApprovalStep s WHERE s.tenantId = :tenantId AND s.decision IS NULL AND "
            + "(s.assigneeEmployeeId = :employeeId OR s.assigneeEmployeeId IS NULL) ORDER BY s.createdAt ASC")
    Page<ApprovalStep> findPendingForAssigneeOrUnassigned(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, Pageable pageable);

    @Query("SELECT s FROM ApprovalStep s WHERE s.tenantId = :tenantId AND s.decision IS NULL AND "
            + "s.assigneeEmployeeId IS NULL ORDER BY s.createdAt ASC")
    Page<ApprovalStep> findUnassignedPendingSteps(@Param("tenantId") UUID tenantId, Pageable pageable);

    List<ApprovalStep> findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(UUID tenantId);

    @Query(value = "SELECT tenant_id FROM core.list_distinct_tenants_with_pending_approval_steps()", nativeQuery = true)
    List<UUID> findDistinctTenantsWithPendingSteps();
}
