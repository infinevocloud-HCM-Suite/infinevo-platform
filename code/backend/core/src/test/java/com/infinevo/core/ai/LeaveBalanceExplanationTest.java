package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.LeaveAllocationRepository;
import com.infinevo.core.leave.LeaveBalanceResponse;
import com.infinevo.core.leave.LeaveBalanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.15: the parts of a leave balance and the sum written out from the service's own figures. */
class LeaveBalanceExplanationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID CL = UUID.randomUUID();
    private static final UUID SL = UUID.randomUUID();

    private static LeaveBalanceResponse balance(UUID type, String code, String name) {
        return new LeaveBalanceResponse(
                EMPLOYEE,
                type,
                code,
                name,
                "2026",
                new BigDecimal("12.00"),
                new BigDecimal("0.00"),
                new BigDecimal("3.00"),
                new BigDecimal("4.5"),
                new BigDecimal("10.50"),
                LocalDate.of(2026, 3, 31),
                null,
                false,
                new BigDecimal("1.00"),
                new BigDecimal("9.50"));
    }

    @Test
    @DisplayName(
            "granted 12, accrued 0, carried 3, used 4.5, pending 1: balance 10.50, available 9.50, 4.5 as \"4.50\"")
    void sentenceAndParts() {
        JsonNode out = LeaveBalanceExplanation.explain(
                List.of(balance(CL, "CL", "Casual Leave")),
                Map.of(
                        CL + "|2026",
                        new LeaveBalanceExplanation.Year(
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null)),
                LocalDate.of(2026, 3, 31));

        JsonNode cl = out.path("balances").get(0);
        assertThat(cl.path("used").asText()).isEqualTo("4.50");
        assertThat(cl.path("balance").asText()).isEqualTo("10.50");
        assertThat(cl.path("available").asText()).isEqualTo("9.50");
        assertThat(cl.path("yearEnd").asText()).isEqualTo("2026-12-31");
        assertThat(cl.path("howItAddsUp").asText())
                .isEqualTo("balance 10.50 = granted 12.00 + accrued 0.00 + carried forward 3.00 - used 4.50;"
                        + " available 9.50 = balance 10.50 - pending 1.00, never below zero");
        assertThat(cl.path("asOf").asText()).isEqualTo("2026-03-31");
        assertThat(cl.path("carryForwardExpired").asBoolean()).isFalse();
        assertThat(cl.path("leaveTypeEnded").asBoolean()).isFalse();
        assertThat(cl.has("expired")).isFalse();
        assertThat(cl.has("proRateFactor")).isFalse();
        assertThat(cl.has("lastAccruedOn")).isFalse();
        assertThat(out.toString())
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-")
                .doesNotContain("opening");
    }

    @Test
    @DisplayName("the day after the carry-forward expiry date it is expired; the leave type's end is its own flag")
    void carryForwardExpiry() {
        JsonNode cl = LeaveBalanceExplanation.explain(
                        List.of(balance(CL, "CL", "Casual Leave")), Map.of(), LocalDate.of(2026, 4, 1))
                .path("balances")
                .get(0);
        assertThat(cl.path("carryForwardExpired").asBoolean()).isTrue();
        assertThat(cl.path("leaveTypeEnded").asBoolean()).isFalse();
        assertThat(cl.has("yearStart")).isFalse();
    }

    private static LeaveBalanceAiToolProvider provider(EmployeeService employees, LeaveBalanceService balances) {
        LeaveAllocationRepository allocations = mock(LeaveAllocationRepository.class);
        when(allocations.findByTenantIdAndEmployeeId(TENANT, EMPLOYEE)).thenReturn(List.of());
        return new LeaveBalanceAiToolProvider(
                balances, allocations, employees, TransactionOperations.withoutTransaction());
    }

    private static EmployeeService me(UUID id) {
        EmployeeService employees = mock(EmployeeService.class);
        EmployeeResponse response = mock(EmployeeResponse.class);
        when(response.id()).thenReturn(id);
        when(employees.currentEmployee()).thenReturn(Optional.of(response));
        return employees;
    }

    @Test
    @DisplayName("the tool: core.leave.read_own; matched by code, then by name; unknown type lists the caller's types")
    void tool() throws Exception {
        LeaveBalanceService balances = mock(LeaveBalanceService.class);
        when(balances.getBalancesForEmployee(eq(TENANT), eq(EMPLOYEE), any()))
                .thenReturn(List.of(balance(CL, "CL", "Casual Leave"), balance(SL, "SL", "Sick Leave")));
        AiTool tool = provider(me(EMPLOYEE), balances).tools().get(0);
        AiToolContext ctx = new AiToolContext(TENANT, UUID.randomUUID(), EMPLOYEE);

        assertThat(tool.name()).isEqualTo("core.explain_leave_balance");
        assertThat(tool.requiredAction()).isEqualTo("core.leave.read_own");
        assertThat(tool.execute(ctx, JSON.readTree("{\"leaveType\":\"cl\"}"))
                        .data()
                        .path("balances"))
                .hasSize(1)
                .first()
                .satisfies(b ->
                        assertThat(b.path("leaveType").path("code").asText()).isEqualTo("CL"));
        assertThat(tool.execute(ctx, JSON.readTree("{\"leaveType\":\"sick leave\"}"))
                        .data()
                        .path("balances"))
                .hasSize(1)
                .first()
                .satisfies(b ->
                        assertThat(b.path("leaveType").path("code").asText()).isEqualTo("SL"));
        JsonNode none = tool.execute(ctx, JSON.readTree("{\"leaveType\":\"Maternity\"}"))
                .data();
        assertThat(none.path("message").asText()).isEqualTo("You have no allocation for that leave type.");
        assertThat(none.path("yourLeaveTypes").findValuesAsText("code")).containsExactly("CL", "SL");
        assertThat(tool.execute(ctx, JSON.readTree("{}")).data().path("balances"))
                .hasSize(2);
        assertThat(tool.execute(ctx, JSON.readTree("{\"employeeId\":\"x\"}")).ok())
                .isFalse();
    }

    @Test
    @DisplayName("no employee record, or a login that is not the context's employee: the plain sentence")
    void noEmployee() throws Exception {
        LeaveBalanceService balances = mock(LeaveBalanceService.class);
        AiToolContext ctx = new AiToolContext(TENANT, UUID.randomUUID(), EMPLOYEE);
        EmployeeService nobody = mock(EmployeeService.class);
        when(nobody.currentEmployee()).thenReturn(Optional.empty());

        assertThat(provider(nobody, balances)
                        .tools()
                        .get(0)
                        .execute(ctx, JSON.readTree("{}"))
                        .error())
                .isEqualTo("No employee record is linked to your login.");
        assertThat(provider(me(UUID.randomUUID()), balances)
                        .tools()
                        .get(0)
                        .execute(ctx, JSON.readTree("{}"))
                        .error())
                .isEqualTo("No employee record is linked to your login.");
        assertThat(provider(me(EMPLOYEE), balances)
                        .tools()
                        .get(0)
                        .execute(new AiToolContext(TENANT, UUID.randomUUID(), null), JSON.readTree("{}"))
                        .error())
                .isEqualTo("No employee record is linked to your login.");
    }
}
