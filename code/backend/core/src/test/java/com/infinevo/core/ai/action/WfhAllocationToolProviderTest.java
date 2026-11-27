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
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.wfh.CreateWfhAllocationRequest;
import com.infinevo.core.wfh.WfhAllocation;
import com.infinevo.core.wfh.WfhAllocationRepository;
import com.infinevo.core.wfh.WfhAllocationResponse;
import com.infinevo.core.wfh.WfhAllocationService;
import com.infinevo.core.wfh.WfhPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.29 step 2, Tests: {@code core.propose_wfh_allocation} and {@link WfhAllocateExecutor}. */
class WfhAllocationToolProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private AdminStepsFixture f;
    private HrStepsPeople people;
    private WfhAllocationRepository allocationRepository;
    private WfhAllocationService service;
    private WfhAllocateExecutor exec;
    private AiTool tool;
    private UUID asha;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(WfhAllocationRepository.class);
        service = mock(WfhAllocationService.class);
        WfhPolicyRepository policies = mock(WfhPolicyRepository.class);
        when(policies.findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(any()))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByTenantIdAndEmployeeIdAndWfhYear(any(), any(), any()))
                .thenReturn(Optional.empty());
        people = new HrStepsPeople();
        exec = new WfhAllocateExecutor(service, allocationRepository, policies, people.repository);
        f = new AdminStepsFixture(List.of(exec));
        asha = people.add("E-001", "Asha", "Rao");
        tool = new WfhAllocationToolProvider(
                        people.query,
                        allocationRepository,
                        () -> LocalDate.parse("2026-11-27"),
                        f.actions,
                        TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool and the action need core.wfh_balance.manage, what POST /api/v1/wfh-allocations needs")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_wfh_allocation");
        assertThat(tool.requiredAction()).isEqualTo("core.wfh_balance.manage");
        assertThat(exec.requiredAction()).isEqualTo("core.wfh_balance.manage");
        assertThat(exec.actionCode()).isEqualTo("core.wfh.allocate");
        assertThat(exec.requiresEmployeeProfile()).isFalse();
    }

    @Test
    @DisplayName("the screen's defaults: this year, 1 Jan to 31 Dec, factor 1; nothing written; no id")
    void propose() throws Exception {
        AiToolResult result = run("{\"employee\":\"E-001\",\"days\":12}");

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString()).contains("Asha Rao (E-001)").contains(WfhAllocateExecutor.DEFAULT_POLICY);
        assertThat(f.stored.get(0).getPayload().toString())
                .isEqualTo("{\"employeeNumber\":\"E-001\",\"wfhYear\":\"2026\",\"yearStartDate\":\"2026-01-01\","
                        + "\"yearEndDate\":\"2026-12-31\",\"entitlementDays\":\"12\",\"proRateFactor\":\"1\"}");
        verify(service, never()).createAllocation(any(), any());
    }

    @Test
    @DisplayName("dates and a factor given are kept; a factor of 0 is sent as 1, as the screen does")
    void given() throws Exception {
        assertThat(run("{\"employee\":\"Asha Rao\",\"year\":2027,\"from\":\"2027-04-01\",\"to\":\"2027-12-31\","
                                + "\"days\":9,\"proRateFactor\":0.75}")
                        .ok())
                .isTrue();
        assertThat(f.stored.get(0).getPayload().path("proRateFactor").asText()).isEqualTo("0.75");
        assertThat(f.stored.get(0).getPayload().path("yearStartDate").asText()).isEqualTo("2027-04-01");
        f.nextMessage();
        assertThat(run("{\"employee\":\"E-001\",\"days\":9,\"proRateFactor\":0}")
                        .ok())
                .isTrue();
        assertThat(f.stored.get(1).getPayload().path("proRateFactor").asText()).isEqualTo("1");
    }

    @Test
    @DisplayName("an overlap, a year taken, an unknown person, bad days or factor get no card; one card per message")
    void refusals() throws Exception {
        when(allocationRepository.existsOverlappingAllocation(eq(f.tenantId), eq(asha), any(), any()))
                .thenReturn(true);
        assertThat(run("{\"employee\":\"E-001\",\"days\":12}").error()).contains("already has");
        when(allocationRepository.existsOverlappingAllocation(eq(f.tenantId), eq(asha), any(), any()))
                .thenReturn(false);
        when(allocationRepository.findByTenantIdAndEmployeeIdAndWfhYear(f.tenantId, asha, "2026"))
                .thenReturn(Optional.of(new WfhAllocation()));
        assertThat(run("{\"employee\":\"E-001\",\"days\":12}").error()).contains("already has");
        assertThat(run("{\"employee\":\"Nobody\",\"days\":12}").error()).isEqualTo(NamedEmployee.NO_EMPLOYEE);
        assertThat(run("{\"employee\":\"E-001\",\"days\":400}").ok()).isFalse();
        assertThat(run("{\"employee\":\"E-001\",\"days\":1,\"proRateFactor\":1.5}")
                        .ok())
                .isFalse();
        assertThat(run("{\"employee\":\"E-001\",\"days\":1,\"from\":\"2026-12-01\",\"to\":\"2026-01-01\"}")
                        .ok())
                .isFalse();
        assertThat(f.stored).isEmpty();

        when(allocationRepository.findByTenantIdAndEmployeeIdAndWfhYear(f.tenantId, asha, "2026"))
                .thenReturn(Optional.empty());
        assertThat(run("{\"employee\":\"E-001\",\"days\":12}").ok()).isTrue();
        assertThat(run("{\"employee\":\"E-001\",\"days\":12}").error()).contains("One step at a time");
        assertThat(f.stored).hasSize(1);
    }

    @Test
    @DisplayName("Confirm calls createAllocation with the screen's request and no policy; its refusals FAIL")
    void execute() throws Exception {
        assertThat(run("{\"employee\":\"E-001\",\"days\":12}").ok()).isTrue();
        var payload = f.stored.get(0).getPayload();
        UUID made = UUID.randomUUID();
        WfhAllocationResponse response = new WfhAllocationResponse(
                made, f.tenantId, asha, null, null, null, null, "2026", null, null, null, null, null, null, null, null);
        when(service.createAllocation(eq(f.tenantId), any())).thenReturn(response);

        assertThat(exec.execute(f.ctx, payload).path("allocationId").asText()).isEqualTo(made.toString());
        ArgumentCaptor<CreateWfhAllocationRequest> sent = ArgumentCaptor.forClass(CreateWfhAllocationRequest.class);
        verify(service).createAllocation(eq(f.tenantId), sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new CreateWfhAllocationRequest(
                        asha,
                        null,
                        "2026",
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"),
                        new BigDecimal("12"),
                        new BigDecimal("1")));

        doThrow(new IllegalArgumentException("An overlapping WFH allocation already exists for this employee."))
                .when(service)
                .createAllocation(eq(f.tenantId), any());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload)).isInstanceOf(IllegalArgumentException.class);

        when(allocationRepository.findByTenantIdAndEmployeeIdAndWfhYear(f.tenantId, asha, "2026"))
                .thenReturn(Optional.of(new WfhAllocation()));
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(WfhAllocateExecutor.SAME_YEAR);

        people.remove("E-001");
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(NamedEmployee.GONE);
    }
}
