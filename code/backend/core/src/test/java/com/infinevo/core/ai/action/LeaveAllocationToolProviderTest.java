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
import com.infinevo.core.leave.LeaveAllocationRepository;
import com.infinevo.core.leave.LeaveAllocationRequest;
import com.infinevo.core.leave.LeaveAllocationResponse;
import com.infinevo.core.leave.LeaveAllocationService;
import com.infinevo.core.leave.LeavePolicy;
import com.infinevo.core.leave.LeavePolicyRepository;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
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

/** W-114.29 step 1, Tests: {@code core.propose_leave_allocation} and {@link LeaveAllocateExecutor}. */
class LeaveAllocationToolProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private AdminStepsFixture f;
    private HrStepsPeople people;
    private LeaveTypeRepository types;
    private LeavePolicyRepository policies;
    private LeaveAllocationRepository allocationRepository;
    private LeaveAllocationService service;
    private LeaveAllocateExecutor exec;
    private AiTool tool;
    private LeaveType casual;
    private UUID asha;
    private int startMonth = 4;

    @BeforeEach
    void setUp() {
        types = mock(LeaveTypeRepository.class);
        policies = mock(LeavePolicyRepository.class);
        allocationRepository = mock(LeaveAllocationRepository.class);
        service = mock(LeaveAllocationService.class);
        people = new HrStepsPeople();
        exec = new LeaveAllocateExecutor(service, people.repository, types);
        f = new AdminStepsFixture(List.of(exec));
        asha = people.add("E-001", "Asha", "Rao");
        people.add("E-002", "Ravi", "Kumar");
        people.add("E-003", "Ravi", "Kumar");
        casual = type("CL", "Casual Leave");
        LeaveType sick = type("SL", "Sick Leave");
        when(types.findByTenantIdAndIsActiveTrueOrderByCodeAsc(f.tenantId)).thenReturn(List.of(casual, sick));
        when(types.findByTenantIdAndCode(f.tenantId, "CL")).thenReturn(Optional.of(casual));
        LeavePolicy policy = mock(LeavePolicy.class);
        when(policies
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policy));
        tool = new LeaveAllocationToolProvider(
                        people.query,
                        types,
                        policies,
                        allocationRepository,
                        () -> LocalDate.parse("2026-11-27"),
                        () -> startMonth,
                        f.actions,
                        TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private LeaveType type(String code, String name) {
        LeaveType t = mock(LeaveType.class);
        UUID id = UUID.randomUUID();
        when(t.getId()).thenReturn(id);
        when(t.getCode()).thenReturn(code);
        when(t.getName()).thenReturn(name);
        when(t.isActive()).thenReturn(true);
        return t;
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool and the action need core.leave_balance.manage, what POST /api/v1/leave-allocations needs")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_leave_allocation");
        assertThat(tool.requiredAction()).isEqualTo("core.leave_balance.manage");
        assertThat(exec.requiredAction()).isEqualTo("core.leave_balance.manage");
        assertThat(exec.actionCode()).isEqualTo("core.leave.allocate");
        assertThat(exec.requiresEmployeeProfile()).isFalse();
    }

    @Test
    @DisplayName("a proposal writes nothing; the payload holds number, type code, year, window and days; no id")
    void propose() throws Exception {
        AiToolResult result = run("{\"employee\":\"asha rao\",\"leaveType\":\"casual leave\",\"days\":7.50}");

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString()).contains("Asha Rao (E-001)").contains("Casual Leave (CL)");
        assertThat(f.stored.get(0).getPayload().toString())
                .isEqualTo("{\"employeeNumber\":\"E-001\",\"leaveTypeCode\":\"CL\",\"leaveYear\":\"2026\","
                        + "\"yearStartDate\":\"2026-04-01\",\"yearEndDate\":\"2027-03-31\",\"openingDays\":\"7.5\"}");
        verify(service, never()).createAllocation(any(), any());
    }

    @Test
    @DisplayName("the window follows the tenant's leave-year start month; a January year is the calendar year")
    void window() throws Exception {
        startMonth = 1;
        assertThat(run("{\"employee\":\"E-001\",\"leaveType\":\"CL\",\"year\":2027,\"days\":12}")
                        .ok())
                .isTrue();
        assertThat(f.stored.get(0).getPayload().path("yearStartDate").asText()).isEqualTo("2027-01-01");
        assertThat(f.stored.get(0).getPayload().path("yearEndDate").asText()).isEqualTo("2027-12-31");
    }

    @Test
    @DisplayName("unknown type lists the types; unknown or soft-deleted person; two of a name lists both numbers")
    void refusals() throws Exception {
        assertThat(run("{\"employee\":\"E-001\",\"leaveType\":\"Vacation\",\"days\":1}")
                        .error())
                .contains("Vacation")
                .contains("Casual Leave (CL)")
                .contains("Sick Leave (SL)");
        assertThat(run("{\"employee\":\"E-999\",\"leaveType\":\"CL\",\"days\":1}")
                        .error())
                .isEqualTo(NamedEmployee.NO_EMPLOYEE);
        people.remove("E-001");
        assertThat(run("{\"employee\":\"Asha Rao\",\"leaveType\":\"CL\",\"days\":1}")
                        .error())
                .isEqualTo(NamedEmployee.NO_EMPLOYEE);
        String many = run("{\"employee\":\"Ravi Kumar\",\"leaveType\":\"CL\",\"days\":1}")
                .error();
        assertThat(many).contains("E-002").contains("E-003");
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(many).find()).isFalse();
        assertThat(run("{\"employee\":\"E-002\",\"leaveType\":\"CL\",\"days\":-1}")
                        .ok())
                .isFalse();
        assertThat(run("{\"employee\":\"E-002\",\"leaveType\":\"CL\",\"days\":1.234}")
                        .ok())
                .isFalse();
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("an overlapping allocation or a type with no policy in effect gets no card")
    void doomed() throws Exception {
        UUID casualId = casual.getId();
        when(allocationRepository.existsOverlappingAllocation(eq(f.tenantId), eq(asha), eq(casualId), any(), any()))
                .thenReturn(true);
        assertThat(run("{\"employee\":\"E-001\",\"leaveType\":\"CL\",\"days\":1}")
                        .error())
                .contains("already has");
        when(policies
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThat(run("{\"employee\":\"E-002\",\"leaveType\":\"CL\",\"days\":1}")
                        .error())
                .contains("no leave policy");
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("one proposal per message")
    void oneAtATime() throws Exception {
        assertThat(run("{\"employee\":\"E-001\",\"leaveType\":\"CL\",\"days\":1}")
                        .ok())
                .isTrue();
        assertThat(run("{\"employee\":\"E-002\",\"leaveType\":\"CL\",\"days\":1}")
                        .error())
                .contains("One step at a time");
        f.nextMessage();
        assertThat(run("{\"employee\":\"E-002\",\"leaveType\":\"CL\",\"days\":1}")
                        .ok())
                .isTrue();
        assertThat(f.stored).hasSize(2);
    }

    @Test
    @DisplayName("Confirm calls createAllocation with the screen's request; its refusals and a removed person FAIL")
    void execute() throws Exception {
        assertThat(run("{\"employee\":\"E-001\",\"leaveType\":\"CL\",\"days\":12}")
                        .ok())
                .isTrue();
        var payload = f.stored.get(0).getPayload();
        UUID made = UUID.randomUUID();
        LeaveAllocationResponse response = new LeaveAllocationResponse(
                made,
                f.tenantId,
                asha,
                casual.getId(),
                "2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(service.createAllocation(eq(f.tenantId), any())).thenReturn(response);

        assertThat(exec.execute(f.ctx, payload).path("allocationId").asText()).isEqualTo(made.toString());
        ArgumentCaptor<LeaveAllocationRequest> sent = ArgumentCaptor.forClass(LeaveAllocationRequest.class);
        verify(service).createAllocation(eq(f.tenantId), sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new LeaveAllocationRequest(
                        asha,
                        casual.getId(),
                        "2026",
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2027-03-31"),
                        new BigDecimal("12")));

        doThrow(new IllegalStateException("An allocation already exists"))
                .when(service)
                .createAllocation(eq(f.tenantId), any());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload)).isInstanceOf(IllegalStateException.class);

        doThrow(new java.util.NoSuchElementException("Leave type not found"))
                .when(service)
                .createAllocation(eq(f.tenantId), any());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(LeaveAllocateExecutor.NO_TYPE);

        people.remove("E-001");
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(NamedEmployee.GONE);
    }
}
