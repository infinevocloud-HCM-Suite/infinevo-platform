package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.HalfDayPeriod;
import com.infinevo.core.leave.LeaveApplyCheck;
import com.infinevo.core.leave.LeaveApplyRequest;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.leave.LeaveUnit;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.8 spec, Tests: {@code core.propose_leave_request} with a real {@link AiActionServiceImpl} over an in-memory
 * intent store. The leave service is a mock, so "nothing written to {@code core.leave_request}" is "createRequest is
 * never called".
 */
class LeaveActionToolProviderTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-25T09:00:00Z");
    private final List<AiActionIntent> stored = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    private LeaveRequestService leaveRequestService;
    private LeaveTypeRepository leaveTypeRepository;
    private AiTool tool;
    private AiToolContext ctx;
    private AiChatTurn turn;
    private LeaveType casual;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        leaveRequestService = mock(LeaveRequestService.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        casual = type("CL", "Casual Leave");
        LeaveType sick = type("SL", "Sick Leave");
        when(leaveTypeRepository.findByTenantIdAndIsActiveTrueOrderByCodeAsc(tenantId))
                .thenReturn(List.of(casual, sick));
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, casual.getId()))
                .thenReturn(java.util.Optional.of(casual));

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
                List.of(new LeaveApplyExecutor(leaveRequestService, leaveTypeRepository)),
                mock(AiAccessGate.class),
                mock(PermissionService.class),
                mock(EntitlementService.class),
                mock(EmployeeService.class),
                mock(AuditWriter.class),
                TransactionOperations.withoutTransaction(),
                Clock.fixed(now, ZoneOffset.UTC));
        tool = new LeaveActionToolProvider(
                        leaveRequestService, leaveTypeRepository, actions, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
        ctx = new AiToolContext(tenantId, userId, employeeId);
        turn = AiChatTurns.open(tenantId, userId, sessionId);
    }

    @AfterEach
    void tearDown() {
        turn.close();
        TenantContext.clear();
    }

    private LeaveType type(String code, String name) {
        LeaveType t =
                new LeaveType(tenantId, code, name, true, LeaveUnit.DAYS, true, LocalDate.of(2025, 1, 1), null, true);
        t.setId(UUID.randomUUID());
        return t;
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool: core.propose_leave_request, needs core.leave.apply and an employee record")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_leave_request");
        assertThat(tool.requiredAction()).isEqualTo("core.leave.apply");
        assertThat(tool.needsEmployee()).isTrue();
        assertThat(tool.inputSchema().path("required").toString())
                .contains("leaveType")
                .contains("fromDate")
                .contains("toDate");
    }

    @Test
    @DisplayName("an unknown leave type: an error listing the valid names; no intent, no check run")
    void unknownType_listsValidNames() throws Exception {
        AiToolResult result = run("{\"leaveType\":\"Holiday\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("Holiday").contains("Casual Leave").contains("Sick Leave");
        assertThat(stored).isEmpty();
        verify(leaveRequestService, never()).validateApply(any(), any(), any());
    }

    @Test
    @DisplayName("the leave type is matched by name, ignoring case")
    void typeMatchedIgnoringCase() throws Exception {
        when(leaveRequestService.validateApply(eq(tenantId), eq(employeeId), any()))
                .thenReturn(new LeaveApplyCheck(casual.getId(), "Casual Leave", false, BigDecimal.ONE));

        AiToolResult result =
                run("{\"leaveType\":\"  casual LEAVE \",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}");

        assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("an ineligible request: the service's reason goes back to the model; no intent is written")
    void ineligible_noIntent() throws Exception {
        when(leaveRequestService.validateApply(eq(tenantId), eq(employeeId), any()))
                .thenThrow(new IllegalArgumentException("Employee is not eligible for leave type: Casual Leave"));

        AiToolResult result =
                run("{\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("Employee is not eligible for leave type: Casual Leave");
        assertThat(stored).isEmpty();
        assertThat(turn.proposed()).isNull();
    }

    @Test
    @DisplayName("an overlap (IllegalStateException) is also a refusal the model reads; no intent")
    void overlap_noIntent() throws Exception {
        when(leaveRequestService.validateApply(any(), any(), any()))
                .thenThrow(new IllegalStateException(
                        "An existing active leave request overlaps with the requested dates"));

        AiToolResult result =
                run("{\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}");

        assertThat(result.error()).contains("overlaps");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("a type needing a document is refused with 'apply on the Leave screen'")
    void documentNeeded() throws Exception {
        when(leaveRequestService.validateApply(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Leave policy requires supporting documents to be attached"));

        AiToolResult result =
                run("{\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}");

        assertThat(result.error())
                .isEqualTo(LeaveActionToolProvider.NEEDS_DOCUMENT)
                .contains("Leave screen");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("a valid one: one PROPOSED intent expiring in 15 minutes; nothing written to core.leave_request")
    void valid_writesProposedIntentOnly() throws Exception {
        when(leaveRequestService.validateApply(eq(tenantId), eq(employeeId), any()))
                .thenReturn(new LeaveApplyCheck(casual.getId(), "Casual Leave", true, new BigDecimal("0.50")));

        AiToolResult result =
                run("{\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\","
                        + "\"halfDay\":true,\"halfDayPeriod\":\"first\",\"reason\":\"School event\"}");

        assertThat(result.ok()).isTrue();
        assertThat(stored).hasSize(1);
        AiActionIntent intent = stored.get(0);
        assertThat(intent.getStatus()).isEqualTo(AiIntentStatus.PROPOSED);
        assertThat(intent.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(intent.getSessionId()).isEqualTo(sessionId);
        assertThat(intent.getUserId()).isEqualTo(userId);
        assertThat(intent.getActionCode()).isEqualTo("core.leave.apply");
        JsonNode payload = intent.getPayload();
        assertThat(payload.path("leaveTypeId").asText())
                .isEqualTo(casual.getId().toString());
        assertThat(payload.path("fromDate").asText()).isEqualTo("2026-12-04");
        assertThat(payload.path("halfDayPeriod").asText()).isEqualTo("first");
        assertThat(payload.path("reason").asText()).isEqualTo("School event");
        assertThat(payload.path("submit").asBoolean()).isTrue();

        ArgumentCaptor<LeaveApplyRequest> checked = ArgumentCaptor.forClass(LeaveApplyRequest.class);
        verify(leaveRequestService).validateApply(eq(tenantId), eq(employeeId), checked.capture());
        assertThat(checked.getValue().halfDayPeriod()).isEqualTo(HalfDayPeriod.FIRST);
        assertThat(checked.getValue().documentIds()).isNull();
        verify(leaveRequestService, never()).createRequest(any(), any(), any());

        assertThat(turn.proposed()).isNotNull();
        assertThat(turn.proposed().intentId()).isEqualTo(intent.getId());
        // The model reads the card and that the user must confirm - never a database id.
        assertThat(result.data().toString())
                .contains("Casual Leave")
                .contains("Confirm")
                .doesNotContain(intent.getId().toString())
                .doesNotContain(casual.getId().toString());
    }

    @Test
    @DisplayName("bad input runs nothing: an unknown field, a bad date, a long reason, a bad half-day period")
    void badInput() throws Exception {
        String base = "\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"";
        assertThat(run("{" + base + ",\"employee\":\"someone\"}").error()).contains("unknown input");
        assertThat(run("{\"leaveType\":\"Casual Leave\",\"fromDate\":\"Friday\",\"toDate\":\"2026-12-04\"}")
                        .error())
                .contains("YYYY-MM-DD");
        assertThat(run("{" + base + ",\"reason\":\"" + "x".repeat(501) + "\"}").error())
                .contains("500");
        assertThat(run("{" + base + ",\"halfDay\":true,\"halfDayPeriod\":\"third\"}")
                        .error())
                .contains("first or second");
        assertThat(run("{\"leaveType\":\"Casual Leave\"}").error()).contains("required");
        assertThat(stored).isEmpty();
        verify(leaveRequestService, never()).validateApply(any(), any(), any());
    }

    @Test
    @DisplayName("a second proposal in one message is refused; one intent stays")
    void secondProposalRefused() throws Exception {
        when(leaveRequestService.validateApply(any(), any(), any()))
                .thenReturn(new LeaveApplyCheck(casual.getId(), "Casual Leave", false, BigDecimal.ONE));
        String input = "{\"leaveType\":\"Casual Leave\",\"fromDate\":\"2026-12-04\",\"toDate\":\"2026-12-04\"}";

        assertThat(run(input).ok()).isTrue();
        AiToolResult second = run(input);

        assertThat(second.ok()).isFalse();
        assertThat(second.error()).contains("already proposed");
        assertThat(stored).hasSize(1);
    }
}
