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
        Integer totalSteps,
        String subjectEmployeeName,
        String assigneeEmployeeName,
        String delegatedFromEmployeeName,
        String escalatedFromEmployeeName,
        String reassignedFromEmployeeName) {

    public static ApprovalStepResponse from(ApprovalStep step) {
        return from(step, null, null);
    }

    public static ApprovalStepResponse from(ApprovalStep step, ApprovalInstance instance) {
        return from(step, instance, null);
    }

    public static ApprovalStepResponse from(ApprovalStep step, ApprovalInstance instance, Integer totalSteps) {
        return from(step, instance, totalSteps, null);
    }

    /**
     * @param subjectEmployeeName who the request is about, so the inbox does not need
     *     {@code core.employee.read} to say so (W-46.4 section 4); null when not looked up
     */
    public static ApprovalStepResponse from(
            ApprovalStep step, ApprovalInstance instance, Integer totalSteps, String subjectEmployeeName) {
        return from(step, instance, totalSteps, subjectEmployeeName, null);
    }

    /**
     * As above, with every person id on the step named from {@code names} (D-79): the assignee and
     * whoever it was delegated, escalated or reassigned from. The caller reads the names in one batch;
     * an id the map does not hold gives a null name.
     */
    public static ApprovalStepResponse from(
            ApprovalStep step,
            ApprovalInstance instance,
            Integer totalSteps,
            String subjectEmployeeName,
            java.util.Map<UUID, String> names) {
        java.util.Map<UUID, String> safe = names != null ? names : java.util.Map.of();
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
                totalSteps,
                subjectEmployeeName,
                nameOf(safe, step.getAssigneeEmployeeId()),
                nameOf(safe, step.getDelegatedFromEmployeeId()),
                nameOf(safe, step.getEscalatedFromEmployeeId()),
                nameOf(safe, step.getReassignedFromEmployeeId()));
    }

    static String nameOf(java.util.Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
