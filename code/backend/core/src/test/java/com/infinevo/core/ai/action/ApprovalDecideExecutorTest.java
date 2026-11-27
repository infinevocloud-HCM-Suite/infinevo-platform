package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.ApprovalFixtures.instance;
import static com.infinevo.core.ai.action.ApprovalFixtures.leave;
import static com.infinevo.core.ai.action.ApprovalFixtures.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.LeaveRequest;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.wfh.WfhRequestRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/** W-114.18 spec, Tests: {@link ApprovalDecideExecutor} - the card from the database, T3, T7, the excluded types. */
class ApprovalDecideExecutorTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID asha = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID leaveId = UUID.randomUUID();
    private final Instant created = Instant.parse("2026-11-24T10:00:00Z");
    private final AiToolContext ctx = new AiToolContext(tenantId, UUID.randomUUID(), null);

    private ApprovalService approvalService;
    private ApprovalStepRepository steps;
    private ApprovalInstanceRepository instances;
    private LeaveRequestRepository leaveRequests;
    private ApprovalDecideExecutor executor;
    private ApprovalStep step;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        steps = mock(ApprovalStepRepository.class);
        instances = mock(ApprovalInstanceRepository.class);
        leaveRequests = mock(LeaveRequestRepository.class);
        EmployeeService employees = mock(EmployeeService.class);
        when(employees.displayNames(anyCollection())).thenReturn(Map.of(asha, "Asha Rao"));
        step = step(stepId, instanceId, created);
        when(steps.findByTenantIdAndId(tenantId, stepId)).thenReturn(Optional.of(step));
        plantInstance(ApprovalFlowType.LEAVE);
        plantLeave("Family function", LeaveRequestStatus.PENDING);
        executor = new ApprovalDecideExecutor(
                approvalService,
                steps,
                instances,
                employees,
                new ApprovalItemReader(
                        leaveRequests, mock(WfhRequestRepository.class), mock(LeaveTypeRepository.class)));
    }

    private void plantInstance(ApprovalFlowType type) {
        var inst = instance(instanceId, type, asha, leaveId);
        when(instances.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(inst));
    }

    private void plantLeave(String reason, LeaveRequestStatus status) {
        LeaveRequest r = leave(UUID.randomUUID(), reason, status);
        when(leaveRequests.findByIdAndTenantId(leaveId, tenantId)).thenReturn(Optional.of(r));
    }

    private JsonNode payload(ApprovalDecision decision, String comment) {
        return ApprovalDecideExecutor.payload(stepId, decision, comment, "PENDING");
    }

    @Test
    @DisplayName("execute: decides the one step with no approvedAmount; answers the instance and decision")
    void execute_neverSendsAmount() {
        JsonNode result = executor.execute(ctx, payload(ApprovalDecision.APPROVED, "ok"));

        ArgumentCaptor<ApprovalDecideRequest> sent = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(stepId), sent.capture());
        assertThat(sent.getValue().approvedAmount()).isNull();
        assertThat(sent.getValue().decision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(sent.getValue().comment()).isEqualTo("ok");
        assertThat(result.path("instanceId").asText()).isEqualTo(instanceId.toString());
        assertThat(result.path("decision").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("an excluded flow type planted behind the step id is refused; decide is never called")
    void excludedType_refused() {
        for (ApprovalFlowType type : new ApprovalFlowType[] {
            ApprovalFlowType.PAY_RUN, ApprovalFlowType.REIMBURSEMENT, ApprovalFlowType.PROOF_OF_INVESTMENT
        }) {
            plantInstance(type);
            assertThatThrownBy(() -> executor.execute(ctx, payload(ApprovalDecision.APPROVED, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(ApprovalDecideExecutor.NOT_HERE);
        }
        verify(approvalService, never()).decide(any(), any());
    }

    @Test
    @DisplayName("T3: describe shows the reason exactly as stored, 2,000 characters and line breaks included")
    void describe_reasonVerbatim() {
        String reason = ("line one\nline two\r\n" + "x".repeat(1_981)).substring(0, 2_000);
        plantLeave(reason, LeaveRequestStatus.PENDING);

        AiActionDescription card = executor.describe(ctx, payload(ApprovalDecision.APPROVED, null));

        assertThat(card.fields())
                .extracting(AiActionField::label)
                .containsExactly(
                        "Request",
                        "Requested by",
                        "Details",
                        "Reason (the employee's own words)",
                        "Waiting since",
                        "Decision",
                        "Comment");
        assertThat(card.fields().get(3).value()).isEqualTo(reason);
        assertThat(card.fields().get(1).value()).isEqualTo("Asha Rao");
        assertThat(card.fields().get(4).value()).isEqualTo("2026-11-24");
        assertThat(card.summary()).isEqualTo("Approve leave request for Asha Rao");
    }

    @Test
    @DisplayName("an hrms request's card says to open it for the note")
    void describe_hrmsType() {
        plantInstance(ApprovalFlowType.OVERTIME);
        when(step.getItemRef()).thenReturn("Overtime 2026-11-20");

        AiActionDescription card = executor.describe(ctx, payload(ApprovalDecision.APPROVED, null));

        assertThat(card.fields().get(2).value()).isEqualTo("Overtime 2026-11-20");
        assertThat(card.fields().get(3).value()).isEqualTo(ApprovalItemReader.OPEN_FOR_NOTE);
    }

    @Test
    @DisplayName("T7: a leave request withdrawn after the proposal fails; decide is never called")
    void withdrawnAfterProposal_changed() {
        plantLeave("Family function", LeaveRequestStatus.WITHDRAWN);

        assertThatThrownBy(() -> executor.execute(ctx, payload(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ApprovalDecideExecutor.CHANGED);
        verify(approvalService, never()).decide(any(), any());
    }

    @Test
    @DisplayName("T7: a step decided by someone else before confirm fails as already decided")
    void decidedElsewhere() {
        when(step.getDecision()).thenReturn(ApprovalDecision.APPROVED);

        assertThatThrownBy(() -> executor.execute(ctx, payload(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ApprovalDecideExecutor.ALREADY_DECIDED);
        verify(approvalService, never()).decide(any(), any());
    }

    @Test
    @DisplayName("the service's refusals become a reason on the card, never an id")
    void serviceRefusals() {
        doThrow(new AccessDeniedException("Caller is not the assigned approver for step " + stepId))
                .when(approvalService)
                .decide(eq(stepId), any());
        assertThatThrownBy(() -> executor.execute(ctx, payload(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ApprovalDecideExecutor.NOT_APPROVER);

        doThrow(new IllegalStateException("Step " + stepId + " has already been decided"))
                .when(approvalService)
                .decide(eq(stepId), any());
        assertThatThrownBy(() -> executor.execute(ctx, payload(ApprovalDecision.APPROVED, null)))
                .hasMessage(ApprovalDecideExecutor.ALREADY_DECIDED);
    }

    @Test
    @DisplayName("an approver needs no employee record; the required action is core.approval.decide")
    void declaration() {
        assertThat(executor.actionCode()).isEqualTo("core.approval.decide");
        assertThat(executor.requiredAction()).isEqualTo("core.approval.decide");
        assertThat(executor.requiresEmployeeProfile()).isFalse();
    }
}
