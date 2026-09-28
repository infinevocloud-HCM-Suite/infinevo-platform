package com.infinevo.core.approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ApprovalInstance} (W-15.2).
 */
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {

    Optional<ApprovalInstance> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ApprovalInstance> findByTenantIdAndSubjectTableAndSubjectId(
            UUID tenantId, String subjectTable, UUID subjectId);

    @Modifying
    @Query(
            "UPDATE ApprovalInstance a SET a.outcomeNotifiedAt = :notifiedAt WHERE a.id = :id AND a.outcomeNotifiedAt IS NULL")
    int claimOutcomeNotification(@Param("id") UUID id, @Param("notifiedAt") Instant notifiedAt);

    @Query(
            "SELECT a FROM ApprovalInstance a WHERE a.tenantId = :tenantId AND a.status IN (com.infinevo.core.approval.InstanceStatus.APPROVED, com.infinevo.core.approval.InstanceStatus.REJECTED) AND a.outcomeNotifiedAt IS NULL")
    List<ApprovalInstance> findPendingOutcomeDispatches(@Param("tenantId") UUID tenantId);

    @Query(value = "SELECT tenant_id FROM core.list_distinct_tenants_with_pending_outcomes()", nativeQuery = true)
    List<UUID> findDistinctTenantsWithPendingOutcomes();
}
