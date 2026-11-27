package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.approval.ApprovalDelegation;
import com.infinevo.core.approval.ApprovalDelegationRepository;
import com.infinevo.core.approval.DelegationCreateRequest;
import com.infinevo.core.approval.DelegationResponse;
import com.infinevo.core.approval.DelegationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.29 step 3, Tests: {@code core.propose_delegation} and {@link DelegationCreateExecutor}. */
class DelegationToolProviderTest {

    private static final String INPUT = "{\"delegate\":\"E-001\",\"from\":\"2026-12-01\",\"to\":\"2026-12-10\","
            + "\"flowTypes\":[\"overtime\",\"Leave\"]}";

    private final ObjectMapper json = new ObjectMapper();
    private AdminStepsFixture f;
    private HrStepsPeople people;
    private ApprovalDelegationRepository repository;
    private DelegationService service;
    private DelegationCreateExecutor exec;
    private AiTool tool;
    private AiToolContext me;
    private UUID asha;
    private UUID meera;

    @BeforeEach
    void setUp() {
        repository = mock(ApprovalDelegationRepository.class);
        service = mock(DelegationService.class);
        people = new HrStepsPeople();
        exec = new DelegationCreateExecutor(service, people.repository);
        f = new AdminStepsFixture(List.of(exec));
        asha = people.add("E-001", "Asha", "Rao");
        meera = people.add("E-100", "Meera", "Shah");
        me = new AiToolContext(f.tenantId, f.userId, meera);
        when(repository.findActiveOverlappingForEmployee(any(), any(), any(), any()))
                .thenReturn(List.of());
        tool = new DelegationToolProvider(
                        people.query, repository, f.actions, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(me, json.readTree(input));
    }

    private ApprovalDelegation delegation(UUID from, UUID to) {
        ApprovalDelegation d = mock(ApprovalDelegation.class);
        when(d.getDelegatorEmployeeId()).thenReturn(from);
        when(d.getDelegateEmployeeId()).thenReturn(to);
        return d;
    }

    @Test
    @DisplayName("the tool and the action need core.approval.delegate and the caller's own employee record")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_delegation");
        assertThat(tool.requiredAction()).isEqualTo("core.approval.delegate");
        assertThat(tool.needsEmployee()).isTrue();
        assertThat(exec.requiredAction()).isEqualTo("core.approval.delegate");
        assertThat(exec.actionCode()).isEqualTo("core.approval.delegate_create");
        assertThat(exec.requiresEmployeeProfile()).isTrue();
    }

    @Test
    @DisplayName("a proposal writes nothing; flow types in the screen's order and codes; the delegate by number; no id")
    void propose() throws Exception {
        AiToolResult result = run(INPUT);

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString()).contains("Asha Rao (E-001)").contains("Leave, Overtime");
        assertThat(f.stored.get(0).getPayload().toString())
                .isEqualTo("{\"delegateEmployeeNumber\":\"E-001\",\"from\":\"2026-12-01\",\"to\":\"2026-12-10\","
                        + "\"flowTypes\":\"LEAVE,OVERTIME\"}");
        verify(service, never()).createDelegation(any(), any(), any());
    }

    @Test
    @DisplayName(
            "oneself, an unknown flow type (lists the real ones), a chain, no flows, or no employee record: no card")
    void refusals() throws Exception {
        assertThat(run(INPUT.replace("E-001", "E-100")).error()).isEqualTo(DelegationCreateExecutor.SELF);
        assertThat(run(INPUT.replace("overtime", "Payslips")).error())
                .contains("Payslips")
                .contains("Leave")
                .contains("Proof of Investment")
                .doesNotContain("Pay Run");
        assertThat(run(INPUT.replace("overtime", "Pay Run")).error())
                .contains(DelegationCreateExecutor.PAY_RUN_ON_SCREEN);
        assertThat(run(INPUT.replace("overtime", "PAY_RUN")).error())
                .contains(DelegationCreateExecutor.PAY_RUN_ON_SCREEN);
        assertThat(run(INPUT.replace("[\"overtime\",\"Leave\"]", "[]")).ok()).isFalse();
        assertThat(run(INPUT.replace("E-001", "E-404")).error()).isEqualTo(NamedEmployee.NO_EMPLOYEE);

        ApprovalDelegation ashaAway = delegation(asha, UUID.randomUUID());
        when(repository.findActiveOverlappingForEmployee(any(), eq(asha), any(), any()))
                .thenReturn(List.of(ashaAway));
        assertThat(run(INPUT).error()).isEqualTo(DelegationCreateExecutor.DELEGATE_IS_DELEGATOR);
        when(repository.findActiveOverlappingForEmployee(any(), eq(asha), any(), any()))
                .thenReturn(List.of());
        ApprovalDelegation meeraStandsIn = delegation(UUID.randomUUID(), meera);
        when(repository.findActiveOverlappingForEmployee(any(), eq(meera), any(), any()))
                .thenReturn(List.of(meeraStandsIn));
        assertThat(run(INPUT).error()).isEqualTo(DelegationCreateExecutor.CALLER_IS_DELEGATE);

        AiToolResult noRecord = tool.execute(f.ctx, json.readTree(INPUT));
        assertThat(noRecord.ok()).isFalse();
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("one proposal per message")
    void oneAtATime() throws Exception {
        assertThat(run(INPUT).ok()).isTrue();
        assertThat(run(INPUT).error()).contains("One step at a time");
        assertThat(f.stored).hasSize(1);
    }

    @Test
    @DisplayName("Confirm calls createDelegation for the caller's record with the screen's request; refusals FAIL")
    void execute() throws Exception {
        assertThat(run(INPUT).ok()).isTrue();
        var payload = f.stored.get(0).getPayload();
        UUID made = UUID.randomUUID();
        DelegationResponse response = new DelegationResponse(
                made, f.tenantId, meera, null, asha, null, "LEAVE,OVERTIME", null, null, true, null, true);
        when(service.createDelegation(eq(f.tenantId), eq(meera), any())).thenReturn(response);

        assertThat(exec.execute(me, payload).path("delegationId").asText()).isEqualTo(made.toString());
        ArgumentCaptor<DelegationCreateRequest> sent = ArgumentCaptor.forClass(DelegationCreateRequest.class);
        verify(service).createDelegation(eq(f.tenantId), eq(meera), sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new DelegationCreateRequest(
                        asha, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-10"), "LEAVE,OVERTIME"));

        doThrow(new IllegalArgumentException("Delegation chains are not allowed"))
                .when(service)
                .createDelegation(eq(f.tenantId), eq(meera), any());
        assertThatThrownBy(() -> exec.execute(me, payload)).isInstanceOf(IllegalArgumentException.class);

        AiToolContext ashaHerself = new AiToolContext(f.tenantId, f.userId, asha);
        assertThatThrownBy(() -> exec.execute(ashaHerself, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(DelegationCreateExecutor.SELF);

        people.remove("E-001");
        assertThatThrownBy(() -> exec.execute(me, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(NamedEmployee.GONE);
    }
}
