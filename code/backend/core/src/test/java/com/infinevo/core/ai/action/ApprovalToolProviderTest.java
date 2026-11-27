package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.ApprovalFixtures.employee;
import static com.infinevo.core.ai.action.ApprovalFixtures.instance;
import static com.infinevo.core.ai.action.ApprovalFixtures.leave;
import static com.infinevo.core.ai.action.ApprovalFixtures.row;
import static com.infinevo.core.ai.action.ApprovalFixtures.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiActionIntent;
import com.infinevo.core.ai.AiActionIntentRepository;
import com.infinevo.core.ai.AiChatTurn;
import com.infinevo.core.ai.AiChatTurns;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.ai.AiUntrustedText;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.approval.ApprovalStepResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.wfh.WfhRequestRepository;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.18 spec, Tests: {@code core.my_pending_approvals} and {@code core.propose_approval_decision} with a real
 * {@link AiActionServiceImpl} over an in-memory intent store. The approval service is a mock: "nothing decided" is
 * "decide is never called".
 */
class ApprovalToolProviderTest {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-27T09:00:00Z");
    private final List<AiActionIntent> stored = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    private final UUID asha = UUID.randomUUID();
    private final UUID ravi = UUID.randomUUID();
    private final UUID leaveStep = UUID.randomUUID();
    private final UUID leaveInstance = UUID.randomUUID();
    private final UUID leaveId = UUID.randomUUID();
    private final UUID payStep = UUID.randomUUID();
    private final UUID otStep = UUID.randomUUID();

    private ApprovalService approvalService;
    private AiTool list;
    private AiTool propose;
    private AiToolContext ctx;
    private AiChatTurn turn;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        approvalService = mock(ApprovalService.class);
        List<ApprovalStepResponse> rows = List.of(
                row(
                        leaveStep,
                        leaveInstance,
                        ApprovalFlowType.LEAVE,
                        asha,
                        leaveId,
                        "Casual Leave 2026-12-07 to 2026-12-08",
                        "Asha Rao",
                        now.minusSeconds(3 * 86_400)),
                row(
                        payStep,
                        UUID.randomUUID(),
                        ApprovalFlowType.PAY_RUN,
                        null,
                        UUID.randomUUID(),
                        "Pay run November",
                        null,
                        now.minusSeconds(86_400)),
                row(
                        otStep,
                        UUID.randomUUID(),
                        ApprovalFlowType.OVERTIME,
                        ravi,
                        UUID.randomUUID(),
                        "Overtime 2026-11-20",
                        "Ravi Kumar",
                        now),
                // As the timesheet flow starts it: the item reference, and so the summary, is a project id.
                row(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ApprovalFlowType.TIMESHEET,
                        ravi,
                        UUID.randomUUID(),
                        UUID.randomUUID().toString(),
                        "Ravi Kumar",
                        now));
        when(approvalService.getPendingSteps(any()))
                .thenAnswer(inv -> new PageImpl<>(rows, PageRequest.of(0, 20), rows.size()));

        EmployeeService employees = mock(EmployeeService.class);
        when(employees.findByIdInIncludingDeleted(anyCollection()))
                .thenReturn(Map.of(asha, employee(asha, "E-014"), ravi, employee(ravi, "E-020")));
        when(employees.displayNames(anyCollection())).thenReturn(Map.of(asha, "Asha Rao", ravi, "Ravi Kumar"));

        LeaveRequestRepository leaveRequests = mock(LeaveRequestRepository.class);
        var pendingLeave = leave(UUID.randomUUID(), "Family function", LeaveRequestStatus.PENDING);
        when(leaveRequests.findByIdAndTenantId(leaveId, tenantId)).thenReturn(Optional.of(pendingLeave));
        ApprovalItemReader items = new ApprovalItemReader(
                leaveRequests, mock(WfhRequestRepository.class), mock(LeaveTypeRepository.class));

        ApprovalStepRepository steps = mock(ApprovalStepRepository.class);
        var firstStep = step(leaveStep, leaveInstance, now.minusSeconds(3 * 86_400));
        when(steps.findByTenantIdAndId(tenantId, leaveStep)).thenReturn(Optional.of(firstStep));
        ApprovalInstanceRepository instances = mock(ApprovalInstanceRepository.class);
        var firstInstance = instance(leaveInstance, ApprovalFlowType.LEAVE, asha, leaveId);
        when(instances.findByTenantIdAndId(tenantId, leaveInstance)).thenReturn(Optional.of(firstInstance));

        AiActionIntentRepository intents = mock(AiActionIntentRepository.class);
        when(intents.save(any(AiActionIntent.class))).thenAnswer(inv -> {
            AiActionIntent i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
                stored.add(i);
            }
            return i;
        });
        AiActionServiceImpl actions = new AiActionServiceImpl(
                intents,
                List.of(new ApprovalDecideExecutor(approvalService, steps, instances, employees, items)),
                mock(AiAccessGate.class),
                mock(PermissionService.class),
                mock(EntitlementService.class),
                employees,
                mock(AuditWriter.class),
                TransactionOperations.withoutTransaction(),
                Clock.fixed(now, ZoneOffset.UTC));
        List<AiTool> tools = new ApprovalToolProvider(
                        approvalService,
                        employees,
                        items,
                        actions,
                        TransactionOperations.withoutTransaction(),
                        Clock.fixed(now, ZoneOffset.UTC))
                .tools();
        list = tools.get(0);
        propose = tools.get(1);
        ctx = new AiToolContext(tenantId, userId, null);
        turn = AiChatTurns.open(tenantId, userId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        turn.close();
        TenantContext.clear();
    }

    private AiToolResult run(AiTool tool, String input) throws Exception {
        return tool.execute(ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tools: both need core.approval.decide and no employee record")
    void declaration() {
        assertThat(list.name()).isEqualTo("core.my_pending_approvals");
        assertThat(propose.name()).isEqualTo("core.propose_approval_decision");
        assertThat(list.requiredAction()).isEqualTo("core.approval.decide");
        assertThat(propose.requiredAction()).isEqualTo("core.approval.decide");
        assertThat(propose.needsEmployee()).isFalse();
    }

    @Test
    @DisplayName("the list: oldest first, no id anywhere, a pay run not decidable and counted for the screen")
    void list_noIds_payRunNotDecidable() throws Exception {
        AiToolResult result = run(list, "{}");

        assertThat(result.ok()).isTrue();
        JsonNode data = result.data();
        assertThat(UUID_TEXT.matcher(data.toString()).find()).isFalse();
        assertThat(data.path("total").asInt()).isEqualTo(4);
        JsonNode rows = data.path("approvals");
        assertThat(rows.get(0).path("position").asInt()).isEqualTo(1);
        assertThat(rows.get(0).path("employeeNumber").asText()).isEqualTo("E-014");
        assertThat(rows.get(0).path("waiting").asText()).isEqualTo("waiting 3 days");
        assertThat(rows.get(0).path("decidable").asBoolean()).isTrue();
        assertThat(rows.get(1).path("flowType").asText()).isEqualTo("PAY_RUN");
        assertThat(rows.get(1).path("decidable").asBoolean()).isFalse();
        assertThat(data.path("note").asText()).startsWith("1 more on the Approvals screen");
        // The timesheet's project id is replaced by the kind of request, in the list and so on the card.
        assertThat(rows.get(3).path("summary").path("quotedText").asText()).isEqualTo("Timesheet");
    }

    @Test
    @DisplayName("T1: summary and reason are never bare strings in the list")
    void list_wrapsOtherPeoplesText() throws Exception {
        JsonNode rows = run(list, "{}").data().path("approvals");

        for (JsonNode row : rows) {
            assertThat(row.path("summary").isObject()).isTrue();
            assertThat(row.path("summary").path("note").asText()).isEqualTo(AiUntrustedText.NOTE);
        }
        assertThat(rows.get(0).path("reason").path("quotedText").asText()).isEqualTo("Family function");
        assertThat(rows.get(0).path("reason").path("note").asText()).isEqualTo(AiUntrustedText.NOTE);
        assertThat(rows.get(2).has("reason")).isFalse();
    }

    @Test
    @DisplayName("propose: one card for the matching row; the payload holds one stepId and no list (T5, T7)")
    void propose_oneStep() throws Exception {
        AiToolResult result = run(
                propose,
                "{\"position\":1,\"employeeNumber\":\"e-014\",\"flowType\":\"LEAVE\",\"decision\":\"APPROVED\"}");

        assertThat(result.ok()).isTrue();
        assertThat(UUID_TEXT.matcher(result.data().toString()).find()).isFalse();
        assertThat(stored).hasSize(1);
        JsonNode payload = stored.get(0).getPayload();
        assertThat(payload.path("stepId").asText()).isEqualTo(leaveStep.toString());
        assertThat(payload.path("expectedStepState").asText()).isEqualTo("PENDING");
        assertThat(payload.path("expectedItemStatus").asText()).isEqualTo("PENDING");
        payload.fields()
                .forEachRemaining(f -> assertThat(f.getValue().isArray()).isFalse());
        JsonNode reasonField = null;
        for (JsonNode f : result.data().path("fields")) {
            if (f.path("label").asText().startsWith("Reason")) {
                reasonField = f;
            }
        }
        assertThat(reasonField).isNotNull();
        assertThat(reasonField.path("value").path("quotedText").asText()).isEqualTo("Family function");
        verify(approvalService, never()).decide(any(), any());
    }

    @Test
    @DisplayName("a pay run, named or at its position, is refused and writes no intent")
    void propose_payRun_refused() throws Exception {
        AiToolResult result = run(
                propose, "{\"position\":2,\"employeeNumber\":\"\",\"flowType\":\"PAY_RUN\",\"decision\":\"APPROVED\"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo(ApprovalToolProvider.NOT_HERE);
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("a position whose row no longer matches is refused with 'the list changed'")
    void propose_mismatch_refused() throws Exception {
        AiToolResult wrongPerson = run(
                propose,
                "{\"position\":1,\"employeeNumber\":\"E-020\",\"flowType\":\"LEAVE\",\"decision\":\"APPROVED\"}");
        AiToolResult wrongType = run(
                propose,
                "{\"position\":3,\"employeeNumber\":\"E-020\",\"flowType\":\"LEAVE\",\"decision\":\"APPROVED\"}");
        AiToolResult beyond = run(
                propose,
                "{\"position\":9,\"employeeNumber\":\"E-020\",\"flowType\":\"LEAVE\",\"decision\":\"APPROVED\"}");

        assertThat(wrongPerson.error()).isEqualTo(ApprovalToolProvider.LIST_CHANGED);
        assertThat(wrongType.error()).isEqualTo(ApprovalToolProvider.LIST_CHANGED);
        assertThat(beyond.error()).isEqualTo(ApprovalToolProvider.NO_ROW);
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("reject with no comment is refused; nothing is written")
    void reject_needsComment() throws Exception {
        AiToolResult result = run(
                propose,
                "{\"position\":1,\"employeeNumber\":\"E-014\",\"flowType\":\"LEAVE\",\"decision\":\"REJECTED\","
                        + "\"comment\":\"  \"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo(ApprovalToolProvider.NEEDS_COMMENT);
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("T6: a second proposal in the same message is refused and writes no intent")
    void secondProposal_refused() throws Exception {
        String input = "{\"position\":1,\"employeeNumber\":\"E-014\",\"flowType\":\"LEAVE\",\"decision\":\"APPROVED\"}";
        AiToolResult first = run(propose, input);
        AiToolResult second = run(
                propose,
                "{\"position\":3,\"employeeNumber\":\"E-020\",\"flowType\":\"OVERTIME\",\"decision\":\"APPROVED\"}");

        assertThat(first.ok()).isTrue();
        assertThat(second.ok()).isFalse();
        assertThat(second.error()).isEqualTo(ApprovalToolProvider.ONE_AT_A_TIME);
        assertThat(stored).hasSize(1);
    }
}
