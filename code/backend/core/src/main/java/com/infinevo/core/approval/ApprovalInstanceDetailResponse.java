package com.infinevo.core.approval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed response representation of an approval instance and its steps (W-15.2).
 */
public record ApprovalInstanceDetailResponse(
        UUID id,
        UUID tenantId,
        ApprovalFlowType flowType,
        UUID definitionId,
        String subjectTable,
        UUID subjectId,
        UUID subjectEmployeeId,
        InstanceStatus status,
        Instant outcomeNotifiedAt,
        Instant startedAt,
        Instant completedAt,
        List<ApprovalStepResponse> steps) {

    public static ApprovalInstanceDetailResponse from(ApprovalInstance instance, List<ApprovalStep> steps) {
        List<ApprovalStepResponse> stepResponses =
                steps.stream().map(ApprovalStepResponse::from).toList();
        return new ApprovalInstanceDetailResponse(
                instance.getId(),
                instance.getTenantId(),
                instance.getFlowType(),
                instance.getDefinitionId(),
                instance.getSubjectTable(),
                instance.getSubjectId(),
                instance.getSubjectEmployeeId(),
                instance.getStatus(),
                instance.getOutcomeNotifiedAt(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                stepResponses);
    }
}
