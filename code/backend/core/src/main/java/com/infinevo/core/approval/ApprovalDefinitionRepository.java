package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link ApprovalDefinition} (W-15.1).
 */
public interface ApprovalDefinitionRepository extends JpaRepository<ApprovalDefinition, UUID> {

    List<ApprovalDefinition> findByTenantId(UUID tenantId);

    List<ApprovalDefinition> findByTenantIdAndFlowType(UUID tenantId, ApprovalFlowType flowType);

    Optional<ApprovalDefinition> findByTenantIdAndFlowTypeAndEffectiveFrom(
            UUID tenantId, ApprovalFlowType flowType, LocalDate effectiveFrom);

    @Query(
            """
        SELECT d FROM ApprovalDefinition d
        WHERE d.tenantId = :tenantId
          AND d.flowType = :flowType
          AND d.isActive = true
          AND d.effectiveFrom <= :asOfDate
        ORDER BY d.effectiveFrom DESC
    """)
    List<ApprovalDefinition> findEffectiveDefinitions(
            @Param("tenantId") UUID tenantId,
            @Param("flowType") ApprovalFlowType flowType,
            @Param("asOfDate") LocalDate asOfDate);

    default Optional<ApprovalDefinition> findEffective(UUID tenantId, ApprovalFlowType flowType, LocalDate asOfDate) {
        List<ApprovalDefinition> list = findEffectiveDefinitions(tenantId, flowType, asOfDate);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
