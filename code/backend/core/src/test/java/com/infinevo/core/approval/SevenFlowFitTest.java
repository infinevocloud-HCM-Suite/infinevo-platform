package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SevenFlowFitTest proves that each of the seven approval flows across the platform
 * (HRMS and Payroll) fits into the unified approval definition model without adapters (W-15.1, spec section 7).
 *
 * <ol>
 *   <li>Leave (HRMS): strict two-stage ladder (reporting manager, then HR).
 *   <li>Overtime (HRMS): permissive two-stage set (reporting manager, HR, either order).
 *   <li>Reimbursement (Payroll): single admin step carrying an approved amount.
 *   <li>Proof of investment (Payroll): per-item HR verification with approved amounts, then final decision.
 *   <li>Attendance regularization: single step to reporting manager (designed flow).
 *   <li>Pay run (Payroll): single admin step (PayRunStatus APPROVAL_PENDING -> APPROVED / REJECTED).
 *   <li>Timesheet (HRMS): single project-manager step (resolved via PROJECT_MANAGER ApproverResolver).
 * </ol>
 */
class SevenFlowFitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Flow 1: Leave fits strict two-stage sequential ladder")
    void leaveFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps = List.of(
                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false),
                new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, false));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.LEAVE);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.PER_STEP);
        assertThat(def.getSteps()).hasSize(2);
        assertThat(def.getSteps().get(0).getKind()).isEqualTo(ApproverKind.REPORTING_MANAGER);
        assertThat(def.getSteps().get(1).getKind()).isEqualTo(ApproverKind.ROLE);
        assertThat(def.getSteps().get(1).getAssignee()).isEqualTo("hr");

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 2: Overtime fits permissive two-stage ANY_ORDER set")
    void overtimeFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps = List.of(
                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false),
                new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, false));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.OVERTIME,
                StepOrdering.ANY_ORDER,
                steps,
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.OVERTIME);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.ANY_ORDER);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.PER_STEP);
        assertThat(def.getSteps()).hasSize(2);

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 3: Reimbursement fits single admin step with SHARED comment")
    void reimbursementFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps =
                List.of(new ApprovalStepDefinition(ApproverKind.ROLE, "tenant-admin", 3, false));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.REIMBURSEMENT,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.SHARED,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.REIMBURSEMENT);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.SHARED);
        assertThat(def.getSteps()).hasSize(1);
        assertThat(def.getSteps().get(0).getKind()).isEqualTo(ApproverKind.ROLE);
        assertThat(def.getSteps().get(0).getAssignee()).isEqualTo("tenant-admin");

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 4: Proof of investment fits per-item stage followed by final decision")
    void proofOfInvestmentFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps = List.of(
                new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, true), // per_item stage
                new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, false) // final decision stage
                );

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.PROOF_OF_INVESTMENT,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.SHARED,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.PROOF_OF_INVESTMENT);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.SHARED);
        assertThat(def.getSteps()).hasSize(2);
        assertThat(def.getSteps().get(0).getPerItem()).isTrue();
        assertThat(def.getSteps().get(1).getPerItem()).isFalse();

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 5: Regularization fits single step to reporting manager")
    void regularizationFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps =
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.REGULARIZATION,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.REGULARIZATION);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.PER_STEP);
        assertThat(def.getSteps()).hasSize(1);
        assertThat(def.getSteps().get(0).getKind()).isEqualTo(ApproverKind.REPORTING_MANAGER);

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 6: Pay run fits single admin step with SHARED comment")
    void payRunFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps =
                List.of(new ApprovalStepDefinition(ApproverKind.ROLE, "payroll-officer", 3, false));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.PAY_RUN,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.SHARED,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.PAY_RUN);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.SHARED);
        assertThat(def.getSteps()).hasSize(1);
        assertThat(def.getSteps().get(0).getKind()).isEqualTo(ApproverKind.ROLE);
        assertThat(def.getSteps().get(0).getAssignee()).isEqualTo("payroll-officer");

        assertJsonRoundTrip(def.getSteps());
    }

    @Test
    @DisplayName("Flow 7: Timesheet fits single PROJECT_MANAGER step")
    void timesheetFlowFits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<ApprovalStepDefinition> steps =
                List.of(new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true));

        ApprovalDefinition def = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.TIMESHEET,
                StepOrdering.SEQUENTIAL,
                steps,
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2026, 1, 1),
                "founder");

        assertThat(def.getFlowType()).isEqualTo(ApprovalFlowType.TIMESHEET);
        assertThat(def.getStepOrdering()).isEqualTo(StepOrdering.SEQUENTIAL);
        assertThat(def.getCommentScope()).isEqualTo(CommentScope.PER_STEP);
        assertThat(def.getSteps()).hasSize(1);
        assertThat(def.getSteps().get(0).getKind()).isEqualTo(ApproverKind.PROJECT_MANAGER);
        assertThat(def.getSteps().get(0).getPerItem())
                .as("one instance per project entry, so the step is per item (W-42.2)")
                .isTrue();

        assertJsonRoundTrip(def.getSteps());
    }

    private void assertJsonRoundTrip(List<ApprovalStepDefinition> steps) throws Exception {
        String json = objectMapper.writeValueAsString(steps);
        List<ApprovalStepDefinition> readBack = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(readBack).isEqualTo(steps);
    }
}
