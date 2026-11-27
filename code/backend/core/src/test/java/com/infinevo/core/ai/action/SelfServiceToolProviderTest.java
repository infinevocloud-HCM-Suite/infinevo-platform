package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.wfh.WfhApplyCheck;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import com.infinevo.core.wfh.WfhRequestStatus;
import com.infinevo.shared.authz.PermissionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.17: the self-service tools find only the caller's own request, refuse what the service would, write no id. */
class SelfServiceToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ANY_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();

    private final LeaveRequestService leave = mock(LeaveRequestService.class);
    private final WfhRequestService wfh = mock(WfhRequestService.class);
    private final AiActionProposals proposals = mock(AiActionProposals.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final AiToolContext ctx = new AiToolContext(TENANT, UUID.randomUUID(), EMPLOYEE);
    private SelfServiceToolProvider provider;

    @BeforeEach
    void setUp() {
        when(permissions.holds(anyString())).thenReturn(true);
        when(leave.searchRequests(eq(TENANT), eq(EMPLOYEE), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(wfh.listEmployeeRequests(TENANT, EMPLOYEE)).thenReturn(List.of());
        when(proposals.propose(any(), anyString(), any()))
                .thenAnswer(inv -> new AiProposedAction(
                        UUID.randomUUID(),
                        inv.getArgument(1),
                        "Do it",
                        List.of(new AiActionField("From", "2026-12-08")),
                        AiIntentStatus.PROPOSED,
                        Instant.parse("2026-11-26T10:15:00Z")));
        provider = new SelfServiceToolProvider(
                leave, wfh, proposals, permissions, TransactionOperations.withoutTransaction(), CLOCK);
    }

    private AiTool tool(String name) {
        return provider.tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private AiToolResult run(String name, String input) throws Exception {
        return tool(name).execute(ctx, JSON.readTree(input));
    }

    private static LeaveRequestResponse leaveOf(LocalDate from, LeaveRequestStatus status) {
        return new LeaveRequestResponse(
                UUID.randomUUID(),
                TENANT,
                EMPLOYEE,
                "Asha",
                UUID.randomUUID(),
                "Casual Leave",
                from,
                from,
                false,
                null,
                BigDecimal.ONE,
                "Family",
                status,
                null,
                false,
                null,
                List.of(),
                null,
                null,
                null,
                null);
    }

    private static WfhRequestResponse wfhOf(LocalDate from, WfhRequestStatus status) {
        return new WfhRequestResponse(
                UUID.randomUUID(),
                TENANT,
                EMPLOYEE,
                "Asha",
                "E1",
                from,
                from,
                false,
                null,
                BigDecimal.ONE,
                "Home",
                status,
                null,
                false,
                null,
                null,
                null);
    }

    private void leaveHas(LeaveRequestResponse... rows) {
        when(leave.searchRequests(eq(TENANT), eq(EMPLOYEE), eq(LeaveRequestStatus.PENDING), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(rows).stream()
                        .filter(r -> r.status() == LeaveRequestStatus.PENDING)
                        .toList()));
        when(leave.searchRequests(eq(TENANT), eq(EMPLOYEE), eq(LeaveRequestStatus.APPROVED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(rows).stream()
                        .filter(r -> r.status() == LeaveRequestStatus.APPROVED)
                        .toList()));
    }

    @Test
    @DisplayName("the six tools, their actions, all needing an employee record")
    void shape() {
        assertThat(provider.tools())
                .extracting(AiTool::name)
                .containsExactly(
                        "core.my_open_requests",
                        "core.propose_wfh_request",
                        "core.propose_wfh_withdraw",
                        "core.propose_wfh_cancel",
                        "core.propose_leave_withdraw",
                        "core.propose_leave_cancel");
        assertThat(tool("core.my_open_requests").requiredAnyOf()).containsExactly("core.wfh.read_own");
        assertThat(tool("core.propose_wfh_cancel").requiredAction()).isEqualTo("core.wfh.apply");
        assertThat(tool("core.propose_leave_cancel").requiredAction()).isEqualTo("core.leave.apply");
        assertThat(provider.tools())
                .allSatisfy(t -> assertThat(t.needsEmployee()).isTrue());
    }

    @Test
    @DisplayName("leave: no request on the date lists the open ones; a pending one is not cancelled; no intent; no id")
    void leaveFindsOnlyTheRightOne() throws Exception {
        LocalDate dec8 = LocalDate.of(2026, 12, 8);
        leaveHas(
                leaveOf(dec8, LeaveRequestStatus.PENDING),
                leaveOf(LocalDate.of(2026, 12, 20), LeaveRequestStatus.APPROVED));

        JsonNode none = run("core.propose_leave_withdraw", "{\"fromDate\":\"2026-12-09\"}")
                .data();
        assertThat(none.path("found").asBoolean(true)).isFalse();
        assertThat(none.path("openRequests").path("leave")).hasSize(2);
        assertThat(ANY_UUID.matcher(none.toString()).find()).isFalse();

        JsonNode wrongStatus = run("core.propose_leave_cancel", "{\"fromDate\":\"2026-12-08\"}")
                .data();
        assertThat(wrongStatus.path("found").asBoolean(true)).isFalse();
        assertThat(wrongStatus.path("message").asText()).contains("approved");
        JsonNode approvedNotWithdrawn = run("core.propose_leave_withdraw", "{\"fromDate\":\"2026-12-20\"}")
                .data();
        assertThat(approvedNotWithdrawn.path("found").asBoolean(true)).isFalse();
        verify(proposals, never()).propose(any(), anyString(), any());

        AiToolResult ok = run("core.propose_leave_withdraw", "{\"fromDate\":\"2026-12-08\",\"reason\":\"Changed\"}");
        assertThat(ok.ok()).isTrue();
        assertThat(ANY_UUID.matcher(ok.data().toString()).find()).isFalse();
        verify(proposals).propose(eq(ctx), eq(LeaveWithdrawExecutor.ACTION_CODE), any());
    }

    @Test
    @DisplayName("leave cancel on the start date is refused with the service's own words; no intent")
    void leaveCancelOnStartDate() throws Exception {
        leaveHas(leaveOf(TODAY, LeaveRequestStatus.APPROVED));
        AiToolResult refused = run("core.propose_leave_cancel", "{\"fromDate\":\"2026-11-26\"}");
        assertThat(refused.ok()).isFalse();
        assertThat(refused.error()).isEqualTo("Leave request cannot be cancelled on or after start date: 2026-11-26");
        verify(proposals, never()).propose(any(), anyString(), any());
    }

    @Test
    @DisplayName("WFH: withdraw finds the pending one; cancel on the start date is refused in the service's words")
    void wfhWithdrawAndCancel() throws Exception {
        when(wfh.listEmployeeRequests(TENANT, EMPLOYEE))
                .thenReturn(List.of(
                        wfhOf(LocalDate.of(2026, 12, 8), WfhRequestStatus.PENDING),
                        wfhOf(TODAY, WfhRequestStatus.APPROVED)));

        assertThat(run("core.propose_wfh_withdraw", "{\"fromDate\":\"2026-12-08\"}")
                        .ok())
                .isTrue();
        verify(proposals).propose(eq(ctx), eq(WfhWithdrawExecutor.ACTION_CODE), any());

        AiToolResult refused = run("core.propose_wfh_cancel", "{\"fromDate\":\"2026-11-26\"}");
        assertThat(refused.error()).isEqualTo("WFH request cannot be cancelled on or after start date: 2026-11-26");

        JsonNode pendingNotCancelled =
                run("core.propose_wfh_cancel", "{\"fromDate\":\"2026-12-08\"}").data();
        assertThat(pendingNotCancelled.path("found").asBoolean(true)).isFalse();
        assertThat(pendingNotCancelled.path("openRequests").path("workFromHome"))
                .hasSize(2);
    }

    @Test
    @DisplayName("WFH apply: the service's refusal comes back in its words and writes no intent; accepted, a card")
    void wfhApply() throws Exception {
        when(wfh.validateApply(eq(TENANT), eq(EMPLOYEE), any()))
                .thenThrow(new IllegalArgumentException(
                        "Employee already has a pending or approved leave request overlapping these dates."));
        AiToolResult refused =
                run("core.propose_wfh_request", "{\"fromDate\":\"2026-12-08\",\"toDate\":\"2026-12-08\"}");
        assertThat(refused.error()).contains("leave request overlapping");
        verify(proposals, never()).propose(any(), anyString(), any());

        when(wfh.validateApply(eq(TENANT), eq(EMPLOYEE), any()))
                .thenReturn(new WfhApplyCheck(new BigDecimal("1.00"), false, null));
        AiToolResult ok = run("core.propose_wfh_request", "{\"fromDate\":\"2026-12-08\",\"toDate\":\"2026-12-08\"}");
        assertThat(ok.ok()).isTrue();
        assertThat(ok.data().path("note").asText()).isEqualTo(SelfServiceToolProvider.NOTE);
        verify(proposals).propose(eq(ctx), eq(WfhApplyExecutor.ACTION_CODE), any());
    }

    @Test
    @DisplayName("my_open_requests leaves out the WFH list without core.wfh.read_own, and lists only from today on")
    void openRequests() throws Exception {
        when(permissions.holds("core.wfh.read_own")).thenReturn(false);
        leaveHas(
                leaveOf(LocalDate.of(2026, 11, 20), LeaveRequestStatus.PENDING),
                leaveOf(TODAY, LeaveRequestStatus.APPROVED));

        JsonNode out = run("core.my_open_requests", "{}").data();

        assertThat(out.has("workFromHome")).isFalse();
        assertThat(out.path("leave")).hasSize(1);
        assertThat(out.path("leave").get(0).path("canCancel").asBoolean()).isFalse();
        assertThat(ANY_UUID.matcher(out.toString()).find()).isFalse();
        verify(wfh, never()).listEmployeeRequests(any(), any());
    }

    @Test
    @DisplayName("no employee record: the plain sentence; an unknown input field is refused")
    void inputs() throws Exception {
        AiToolResult noEmployee = tool("core.my_open_requests")
                .execute(new AiToolContext(TENANT, UUID.randomUUID(), null), JSON.readTree("{}"));
        assertThat(noEmployee.error()).isEqualTo(SelfServiceToolProvider.NO_EMPLOYEE);
        assertThat(run("core.propose_leave_cancel", "{\"fromDate\":\"2026-12-08\",\"requestId\":\"x\"}")
                        .ok())
                .isFalse();
    }
}
