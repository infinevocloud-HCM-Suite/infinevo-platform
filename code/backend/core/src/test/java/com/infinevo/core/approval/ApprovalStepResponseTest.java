package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalStepResponseTest {

    @Test
    @DisplayName("ApprovalStepResponse.from enriches response with instance metadata")
    void enrichesResponseWithInstanceMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID subjectEmpId = UUID.randomUUID();

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                definitionId,
                new SubjectRef("leave_request", subjectId),
                subjectEmpId);
        instance.setId(instanceId);

        ApprovalStep step = new ApprovalStep(
                tenantId, instanceId, 0, "Annual Leave 3 days", ApproverKind.REPORTING_MANAGER, UUID.randomUUID());
        step.setId(UUID.randomUUID());

        ApprovalStepResponse response = ApprovalStepResponse.from(step, instance);

        assertThat(response.id()).isEqualTo(step.getId());
        assertThat(response.instanceId()).isEqualTo(instanceId);
        assertThat(response.flowType()).isEqualTo(ApprovalFlowType.LEAVE);
        assertThat(response.subjectEmployeeId()).isEqualTo(subjectEmpId);
        assertThat(response.itemId()).isEqualTo(subjectId);
        assertThat(response.summary()).isEqualTo("Annual Leave 3 days");
        assertThat(response.subjectEmployeeName()).isNull();
    }

    @Test
    @DisplayName("ApprovalStepResponse.from carries the subject's name and the step count when given them")
    void carriesSubjectNameAndTotalSteps() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID subjectEmpId = UUID.randomUUID();

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                UUID.randomUUID(),
                new SubjectRef("leave_request", UUID.randomUUID()),
                subjectEmpId);
        instance.setId(instanceId);

        ApprovalStep step =
                new ApprovalStep(tenantId, instanceId, 1, null, ApproverKind.REPORTING_MANAGER, UUID.randomUUID());
        step.setId(UUID.randomUUID());

        ApprovalStepResponse response = ApprovalStepResponse.from(step, instance, 3, "Asha Rao");

        assertThat(response.subjectEmployeeId()).isEqualTo(subjectEmpId);
        assertThat(response.subjectEmployeeName()).isEqualTo("Asha Rao");
        assertThat(response.totalSteps()).isEqualTo(3);
    }

    @Test
    @DisplayName("ApprovalStepResponse.from generates fallback summary when itemRef is absent")
    void generatesFallbackSummaryWhenItemRefAbsent() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID subjectEmpId = UUID.randomUUID();

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.REGULARIZATION,
                definitionId,
                new SubjectRef("attendance_regularization", subjectId),
                subjectEmpId);
        instance.setId(instanceId);

        ApprovalStep step =
                new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, UUID.randomUUID());
        step.setId(UUID.randomUUID());

        ApprovalStepResponse response = ApprovalStepResponse.from(step, instance);

        assertThat(response.flowType()).isEqualTo(ApprovalFlowType.REGULARIZATION);
        assertThat(response.summary()).isEqualTo("REGULARIZATION Request");
        assertThat(response.itemId()).isEqualTo(subjectId);
    }
}
