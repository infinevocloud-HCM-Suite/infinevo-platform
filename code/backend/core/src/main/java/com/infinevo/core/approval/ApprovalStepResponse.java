package com.infinevo.core.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response representation of an approval step (W-15.2).
 */
public record ApprovalStepResponse(
        UUID id,
        UUID instanceId,
        int stepIndex,
        String itemRef,
        ApproverKind approverKind,
        UUID assigneeEmployeeId,
        UUID delegatedFromEmployeeId,
        UUID escalatedFromEmployeeId,
        UUID reassignedFromEmployeeId,
        String reassignReason,
        ApprovalDecision decision,
        String comment,
        BigDecimal approvedAmount,
        Instant decidedAt,
        Instant createdAt,
        ApprovalFlowType flowType,
        UUID subjectEmployeeId,
        UUID itemId,
        String summary,
        Integer totalSteps) {

    public static ApprovalStepResponse from(ApprovalStep step) {
        return from(step, null, null);
    }

    public static ApprovalStepResponse from(ApprovalStep step, ApprovalInstance instance) {
        return from(step, instance, null);
    }

    public static ApprovalStepResponse from(ApprovalStep step, ApprovalInstance instance, Integer totalSteps) {
        ApprovalFlowType flowType = instance != null ? instance.getFlowType() : null;
        UUID subjectEmployeeId = instance != null ? instance.getSubjectEmployeeId() : null;
        UUID itemId = instance != null ? instance.getSubjectId() : null;
        String summary = (step.getItemRef() != null && !step.getItemRef().isBlank())
                ? step.getItemRef()
                : (instance != null ? (instance.getFlowType() + " Request") : null);

        return new ApprovalStepResponse(
                step.getId(),
                step.getInstanceId(),
                step.getStepIndex(),
                step.getItemRef(),
                step.getApproverKind(),
                step.getAssigneeEmployeeId(),
                step.getDelegatedFromEmployeeId(),
                step.getEscalatedFromEmployeeId(),
                step.getReassignedFromEmployeeId(),
                step.getReassignReason(),
                step.getDecision(),
                step.getComment(),
                step.getApprovedAmount(),
                step.getDecidedAt(),
                step.getCreatedAt(),
                flowType,
                subjectEmployeeId,
                itemId,
                summary,
                totalSteps);
    }
}
