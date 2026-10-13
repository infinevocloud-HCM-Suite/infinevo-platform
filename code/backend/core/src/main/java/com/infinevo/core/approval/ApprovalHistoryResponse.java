package com.infinevo.core.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detailed history response showing the lifecycle trail of an approval instance (W-15.3, spec section 4).
 * D-79: every person id carries a display name beside it; {@code assigneeName} is D-71's.
 */
public record ApprovalHistoryResponse(
        UUID instanceId,
        ApprovalFlowType flowType,
        InstanceStatus status,
        String subjectTable,
        UUID subjectId,
        UUID subjectEmployeeId,
        String subjectEmployeeName,
        Instant startedAt,
        Instant completedAt,
        List<ApprovalHistoryStepResponse> steps) {

    public record ApprovalHistoryStepResponse(
            UUID stepId,
            int stepIndex,
            String itemRef,
            ApproverKind approverKind,
            UUID assigneeEmployeeId,
            String assigneeName,
            UUID delegatedFromEmployeeId,
            String delegatedFromEmployeeName,
            UUID escalatedFromEmployeeId,
            String escalatedFromEmployeeName,
            UUID reassignedFromEmployeeId,
            String reassignedFromEmployeeName,
            String reassignReason,
            ApprovalDecision decision,
            String comment,
            BigDecimal approvedAmount,
            Instant decidedAt,
            Instant createdAt) {

        /** {@code assigneeName} is the assignee's display name (D-71), or null when the step has no assignee. */
        public static ApprovalHistoryStepResponse from(ApprovalStep step, String assigneeName) {
            return from(step, assigneeName, null);
        }

        /**
         * With every person on the step named from {@code names} (D-79), read by the service in one batch
         * for the whole trail; an id the map does not hold gives a null name.
         */
        public static ApprovalHistoryStepResponse from(ApprovalStep step, Map<UUID, String> names) {
            Map<UUID, String> safe = names != null ? names : Map.of();
            return from(step, ApprovalStepResponse.nameOf(safe, step.getAssigneeEmployeeId()), safe);
        }

        private static ApprovalHistoryStepResponse from(
                ApprovalStep step, String assigneeName, Map<UUID, String> names) {
            Map<UUID, String> safe = names != null ? names : Map.of();
            return new ApprovalHistoryStepResponse(
                    step.getId(),
                    step.getStepIndex(),
                    step.getItemRef(),
                    step.getApproverKind(),
                    step.getAssigneeEmployeeId(),
                    assigneeName,
                    step.getDelegatedFromEmployeeId(),
                    ApprovalStepResponse.nameOf(safe, step.getDelegatedFromEmployeeId()),
                    step.getEscalatedFromEmployeeId(),
                    ApprovalStepResponse.nameOf(safe, step.getEscalatedFromEmployeeId()),
                    step.getReassignedFromEmployeeId(),
                    ApprovalStepResponse.nameOf(safe, step.getReassignedFromEmployeeId()),
                    step.getReassignReason(),
                    step.getDecision(),
                    step.getComment(),
                    step.getApprovedAmount(),
                    step.getDecidedAt(),
                    step.getCreatedAt());
        }
    }
}
