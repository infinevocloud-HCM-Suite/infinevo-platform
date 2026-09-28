package com.infinevo.core.approval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response representation for an approval definition (W-15.1, spec section 4).
 */
public record ApprovalDefinitionResponse(
        UUID id,
        UUID tenantId,
        ApprovalFlowType flowType,
        StepOrdering stepOrdering,
        CommentScope commentScope,
        boolean isActive,
        LocalDate effectiveFrom,
        List<ApprovalStepDefinition> steps,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static ApprovalDefinitionResponse from(ApprovalDefinition entity) {
        return new ApprovalDefinitionResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getFlowType(),
                entity.getStepOrdering(),
                entity.getCommentScope(),
                entity.isActive(),
                entity.getEffectiveFrom(),
                entity.getSteps(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
