package com.infinevo.core.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed history response showing the lifecycle trail of an approval instance (W-15.3, spec section 4).
 */
public record ApprovalHistoryResponse(
        UUID instanceId,
        ApprovalFlowType flowType,
        InstanceStatus status,
        String subjectTable,
        UUID subjectId,
        UUID subjectEmployeeId,
        Instant startedAt,
        Instant completedAt,
        List<ApprovalHistoryStepResponse> steps) {

    public record ApprovalHistoryStepResponse(
            UUID stepId,
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
            Instant createdAt) {

        public static ApprovalHistoryStepResponse from(ApprovalStep step) {
            return new ApprovalHistoryStepResponse(
                    step.getId(),
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
                    step.getCreatedAt());
        }
    }
}
