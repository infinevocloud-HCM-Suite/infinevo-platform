package com.infinevo.core.approval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detailed response representation of an approval instance and its steps (W-15.2), each person id
 * with its display name beside it (D-79).
 */
public record ApprovalInstanceDetailResponse(
        UUID id,
        UUID tenantId,
        ApprovalFlowType flowType,
        UUID definitionId,
        String subjectTable,
        UUID subjectId,
        UUID subjectEmployeeId,
        String subjectEmployeeName,
        InstanceStatus status,
        Instant outcomeNotifiedAt,
        Instant startedAt,
        Instant completedAt,
        List<ApprovalStepResponse> steps) {

    public static ApprovalInstanceDetailResponse from(ApprovalInstance instance, List<ApprovalStep> steps) {
        return from(instance, steps, null);
    }

    /**
     * With every person named from {@code names} (D-79): the subject employee - the requester - and,
     * on each step, the assignee and whoever it was delegated, escalated or reassigned from. The
     * service reads the names in one batch; an id the map does not hold gives a null name.
     */
    public static ApprovalInstanceDetailResponse from(
            ApprovalInstance instance, List<ApprovalStep> steps, Map<UUID, String> names) {
        Map<UUID, String> safe = names != null ? names : Map.of();
        List<ApprovalStepResponse> stepResponses = steps.stream()
                .map(step -> ApprovalStepResponse.from(
                        step, instance, null, ApprovalStepResponse.nameOf(safe, instance.getSubjectEmployeeId()), safe))
                .toList();
        return new ApprovalInstanceDetailResponse(
                instance.getId(),
                instance.getTenantId(),
                instance.getFlowType(),
                instance.getDefinitionId(),
                instance.getSubjectTable(),
                instance.getSubjectId(),
                instance.getSubjectEmployeeId(),
                ApprovalStepResponse.nameOf(safe, instance.getSubjectEmployeeId()),
                instance.getStatus(),
                instance.getOutcomeNotifiedAt(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                stepResponses);
    }
}
