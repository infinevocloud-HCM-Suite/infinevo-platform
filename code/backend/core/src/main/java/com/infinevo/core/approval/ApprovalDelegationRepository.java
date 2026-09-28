package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ApprovalDelegation} (W-15.3).
 */
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    Optional<ApprovalDelegation> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ApprovalDelegation> findByTenantIdAndDelegatorEmployeeId(UUID tenantId, UUID delegatorEmployeeId);

    @Query(
            """
            SELECT d FROM ApprovalDelegation d
            WHERE d.tenantId = :tenantId
              AND d.delegatorEmployeeId = :delegatorId
              AND d.isActive = true
              AND d.effectiveFrom <= :date
              AND d.effectiveTo >= :date
            ORDER BY d.createdAt DESC
            """)
    List<ApprovalDelegation> findActiveByDelegator(
            @Param("tenantId") UUID tenantId, @Param("delegatorId") UUID delegatorId, @Param("date") LocalDate date);

    @Query(
            """
            SELECT d FROM ApprovalDelegation d
            WHERE d.tenantId = :tenantId
              AND d.isActive = true
              AND d.effectiveFrom <= :to
              AND d.effectiveTo >= :from
              AND (d.delegatorEmployeeId = :employeeId OR d.delegateEmployeeId = :employeeId)
            """)
    List<ApprovalDelegation> findActiveOverlappingForEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            """
            SELECT d FROM ApprovalDelegation d
            WHERE d.tenantId = :tenantId
              AND (:employeeId IS NULL OR d.delegatorEmployeeId = :employeeId OR d.delegateEmployeeId = :employeeId)
              AND (cast(:activeOn as date) IS NULL OR (d.isActive = true AND d.effectiveFrom <= :activeOn AND d.effectiveTo >= :activeOn))
            ORDER BY d.effectiveFrom DESC
            """)
    List<ApprovalDelegation> searchDelegations(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("activeOn") LocalDate activeOn);
}
