package com.infinevo.core.ai.action;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepResponse;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.leave.LeaveRequest;
import com.infinevo.core.leave.LeaveRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Shared rows for the W-114.18 unit tests: steps, instances, inbox rows and leave requests as mocks or records. */
final class ApprovalFixtures {

    private ApprovalFixtures() {}

    static ApprovalStepResponse row(
            UUID stepId,
            UUID instanceId,
            ApprovalFlowType type,
            UUID subjectEmployeeId,
            UUID itemId,
            String summary,
            String name,
            Instant createdAt) {
        return new ApprovalStepResponse(
                stepId,
                instanceId,
                0,
                summary,
                ApproverKind.values()[0],
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                type,
                subjectEmployeeId,
                itemId,
                summary,
                1,
                name,
                null,
                null,
                null,
                null);
    }

    static ApprovalStep step(UUID stepId, UUID instanceId, Instant createdAt) {
        ApprovalStep step = mock(ApprovalStep.class);
        when(step.getId()).thenReturn(stepId);
        when(step.getInstanceId()).thenReturn(instanceId);
        when(step.getCreatedAt()).thenReturn(createdAt);
        return step;
    }

    static ApprovalInstance instance(UUID instanceId, ApprovalFlowType type, UUID subjectEmployeeId, UUID itemId) {
        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getFlowType()).thenReturn(type);
        when(instance.getSubjectEmployeeId()).thenReturn(subjectEmployeeId);
        when(instance.getSubjectId()).thenReturn(itemId);
        return instance;
    }

    static LeaveRequest leave(UUID leaveTypeId, String reason, LeaveRequestStatus status) {
        LeaveRequest r = mock(LeaveRequest.class);
        when(r.getLeaveTypeId()).thenReturn(leaveTypeId);
        when(r.getFromDate()).thenReturn(LocalDate.of(2026, 12, 7));
        when(r.getToDate()).thenReturn(LocalDate.of(2026, 12, 8));
        when(r.getReason()).thenReturn(reason);
        when(r.getStatus()).thenReturn(status);
        return r;
    }

    static EmployeeResponse employee(UUID id, String number) {
        return new EmployeeResponse(
                id, null, number, "Asha", null, "Rao", null, null, null, null, null, null, false, null, null, null,
                null, null, null, null, null);
    }
}
