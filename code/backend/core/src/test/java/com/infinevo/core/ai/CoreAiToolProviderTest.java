package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.holiday.HolidayQueryService;
import com.infinevo.core.holiday.HolidayResponse;
import com.infinevo.core.leave.HalfDayPeriod;
import com.infinevo.core.leave.LeaveBalanceResponse;
import com.infinevo.core.leave.LeaveBalanceService;
import com.infinevo.core.leave.LeaveEligibilityService;
import com.infinevo.core.leave.LeavePolicyEligibilityResponse;
import com.infinevo.core.leave.LeavePolicyResponse;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.leave.LeaveTypeResponse;
import com.infinevo.core.leave.LeaveUnit;
import com.infinevo.core.leave.OverdrawnEmployee;
import com.infinevo.core.org.DepartmentResponse;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.core.wfh.WfhBalanceService;
import com.infinevo.core.wfh.WfhBalanceSummaryResponse;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import com.infinevo.core.wfh.WfhRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.4 spec, Tests: {@link CoreAiToolProvider} - each tool calls its service with the caller's own ids, exactly as
 * the screen's controller does, and its answer holds no database id (rule 1). Bad input runs nothing.
 */
class CoreAiToolProviderTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private final ObjectMapper json = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    private LeaveBalanceService balances;
    private LeaveRequestService requests;
    private LeaveEligibilityService eligibility;
    private HolidayQueryService holidays;
    private DepartmentService departments;
    private DesignationService designations;
    private WorkLocationService locations;
    private WfhRequestService wfhRequests;
    private WfhBalanceService wfhBalances;
    private EmployeeService employees;
    private CoreAiToolProvider provider;
    private AiToolContext caller;

    @BeforeEach
    void setUp() {
        balances = mock(LeaveBalanceService.class);
        requests = mock(LeaveRequestService.class);
        eligibility = mock(LeaveEligibilityService.class);
        holidays = mock(HolidayQueryService.class);
        departments = mock(DepartmentService.class);
        designations = mock(DesignationService.class);
        locations = mock(WorkLocationService.class);
        wfhRequests = mock(WfhRequestService.class);
        wfhBalances = mock(WfhBalanceService.class);
        employees = mock(EmployeeService.class);
        when(employees.currentEmployee()).thenReturn(Optional.of(me()));
        TenantClock clock = mock(TenantClock.class);
        when(clock.today()).thenReturn(TODAY);
        provider = new CoreAiToolProvider(
                balances,
                requests,
                eligibility,
                holidays,
                departments,
                designations,
                locations,
                wfhRequests,
                wfhBalances,
                employees,
                clock,
                TransactionOperations.withoutTransaction());
        caller = new AiToolContext(tenantId, userId, employeeId);
    }

    @Test
    @DisplayName("seven tools, named and guarded as the spec's table says; own-data tools need an employee record")
    void sevenTools_asSpecified() {
        assertThat(provider.tools())
                .extracting(AiTool::name, AiTool::requiredAction, AiTool::needsEmployee)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("core.my_leave_balances", "core.leave.read_own", true),
                        org.assertj.core.groups.Tuple.tuple("core.my_leave_requests", "core.leave.read_own", true),
                        org.assertj.core.groups.Tuple.tuple("core.leave_types_for_me", "core.leave.apply", true),
                        org.assertj.core.groups.Tuple.tuple("core.holidays", "core.holiday.read", false),
                        org.assertj.core.groups.Tuple.tuple("core.org_lookup", "core.org.read", false),
                        org.assertj.core.groups.Tuple.tuple("core.my_wfh_requests", "core.wfh.read_own", true),
                        org.assertj.core.groups.Tuple.tuple("core.my_wfh_balance", "core.wfh.read_own", true));
        for (AiTool tool : provider.tools()) {
            JsonNode schema = tool.inputSchema();
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
            // No input may name an employee, or any other row (rule 1).
            schema.path("properties").fieldNames().forEachRemaining(name -> assertThat(name)
                    .doesNotContainIgnoringCase("employee")
                    .doesNotEndWith("Id")
                    .isNotEqualTo("id"));
            assertThat(tool.description()).isNotBlank();
        }
    }

    @Test
    @DisplayName("the provider passes the registry's startup checks")
    void registryAcceptsTheTools() {
        AiToolRegistry registry = new AiToolRegistry(
                mock(com.infinevo.shared.authz.PermissionService.class),
                mock(com.infinevo.shared.entitlement.EntitlementService.class),
                List.of(provider));
        assertThat(registry.find("core.my_wfh_balance")).isPresent();
    }

    @Test
    @DisplayName("my_leave_balances: the caller's employee id and asOf; figures kept, every id removed")
    void myLeaveBalances() {
        LocalDate asOf = LocalDate.of(2026, 12, 31);
        when(balances.getBalancesForEmployee(tenantId, employeeId, asOf)).thenReturn(List.of(balance()));

        JsonNode data = ok(run("core.my_leave_balances", "{\"asOf\":\"2026-12-31\"}"));

        verify(balances).getBalancesForEmployee(tenantId, employeeId, asOf);
        assertNoIds(data);
        assertThat(data.get(0).path("leaveTypeName").asText()).isEqualTo("Casual Leave");
        assertThat(data.get(0).path("availableDays").decimalValue()).isEqualByComparingTo("7.5");
        assertThat(data.get(0).has("employeeId")).isFalse();
        assertThat(data.get(0).has("leaveTypeId")).isFalse();
    }

    @Test
    @DisplayName("my_leave_balances with no asOf passes null, as GET /me/leave-balances does")
    void myLeaveBalances_noAsOf() {
        when(balances.getBalancesForEmployee(tenantId, employeeId, null)).thenReturn(List.of());
        ok(run("core.my_leave_balances", "{}"));
        verify(balances).getBalancesForEmployee(tenantId, employeeId, null);
    }

    @Test
    @DisplayName("a bad date, an unknown input (employeeId) or a non-object runs nothing and answers a short reason")
    void badInput_runsNothing() {
        AiToolResult badDate = run("core.my_leave_balances", "{\"asOf\":\"31/12/2026\"}");
        assertThat(badDate.ok()).isFalse();
        assertThat(badDate.error()).contains("asOf").contains("YYYY-MM-DD");

        AiToolResult someoneElse = run("core.my_leave_balances", "{\"employeeId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(someoneElse.ok()).isFalse();
        assertThat(someoneElse.error()).contains("unknown input 'employeeId'");

        AiToolResult notObject = tool("core.my_wfh_requests")
                .execute(caller, json.getNodeFactory().arrayNode());
        assertThat(notObject.ok()).isFalse();

        AiToolResult number = run("core.my_wfh_balance", "{\"asOf\":20261231}");
        assertThat(number.ok()).isFalse();

        verifyNoInteractions(balances, wfhRequests, wfhBalances);
    }

    @Test
    @DisplayName("an own-data tool run with no employee record answers a failure and calls nothing")
    void ownData_withoutEmployee_runsNothing() {
        AiToolContext noEmployee = new AiToolContext(tenantId, userId, null);
        for (String name : List.of(
                "core.my_leave_balances",
                "core.my_leave_requests",
                "core.leave_types_for_me",
                "core.my_wfh_requests",
                "core.my_wfh_balance")) {
            assertThat(tool(name).execute(noEmployee, json.createObjectNode()).ok())
                    .as(name)
                    .isFalse();
        }
        verifyNoInteractions(balances, requests, eligibility, wfhRequests, wfhBalances);
    }

    @Test
    @DisplayName("my_leave_requests: the caller's id, status and range, first page of 25; no id in the answer")
    void myLeaveRequests() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        Pageable first25 = PageRequest.of(0, 25);
        when(requests.searchRequests(tenantId, employeeId, LeaveRequestStatus.APPROVED, from, to, first25))
                .thenReturn(new PageImpl<>(List.of(leaveRequest()), first25, 40));

        JsonNode data = ok(run(
                "core.my_leave_requests", "{\"status\":\"approved\",\"from\":\"2026-01-01\",\"to\":\"2026-12-31\"}"));

        verify(requests).searchRequests(tenantId, employeeId, LeaveRequestStatus.APPROVED, from, to, first25);
        assertNoIds(data);
        assertThat(data.path("total").asLong()).isEqualTo(40);
        assertThat(data.path("shown").asInt()).isEqualTo(1);
        JsonNode row = data.path("requests").get(0);
        assertThat(row.path("status").asText()).isEqualTo("APPROVED");
        assertThat(row.path("fromDate").asText()).isEqualTo("2026-03-02");
        assertThat(row.path("workingDays").decimalValue()).isEqualByComparingTo("2");
        assertThat(row.has("documentIds")).isFalse();
        assertThat(row.has("createdBy")).isFalse();
    }

    @Test
    @DisplayName("my_leave_requests: an unknown status, or to before from, runs nothing")
    void myLeaveRequests_badInput() {
        assertThat(run("core.my_leave_requests", "{\"status\":\"LOST\"}").error())
                .contains("status must be one of");
        assertThat(run("core.my_leave_requests", "{\"from\":\"2026-05-01\",\"to\":\"2026-04-01\"}")
                        .error())
                .contains("on or after");
        verifyNoInteractions(requests);
    }

    @Test
    @DisplayName("leave_types_for_me: the caller's id and today; policy kept without eligibility, overdrawn or ids")
    void leaveTypesForMe() {
        when(eligibility.getEligibleLeaveTypes(tenantId, employeeId, TODAY)).thenReturn(List.of(leaveType()));

        JsonNode data = ok(run("core.leave_types_for_me", "{}"));

        verify(eligibility).getEligibleLeaveTypes(tenantId, employeeId, TODAY);
        assertNoIds(data);
        assertThat(data.get(0).path("name").asText()).isEqualTo("Casual Leave");
        JsonNode policy = data.get(0).path("policy");
        assertThat(policy.path("annualDays").decimalValue()).isEqualByComparingTo("12");
        assertThat(policy.has("eligibility")).isFalse();
        assertThat(policy.has("overdrawnEmployees")).isFalse();
        assertThat(data.toString()).doesNotContain("overdrawnDays");
    }

    @Test
    @DisplayName("holidays: today to 12 months ahead on the caller's own location; holiday and calendar ids removed")
    void holidays_defaults() {
        when(holidays.holidaysBetween(locationId, TODAY, TODAY.plusMonths(12))).thenReturn(List.of(holiday()));

        JsonNode data = ok(run("core.holidays", "{}"));

        verify(holidays).holidaysBetween(locationId, TODAY, TODAY.plusMonths(12));
        assertNoIds(data);
        assertThat(data.get(0).path("name").asText()).isEqualTo("Diwali");
        assertThat(data.get(0).path("isRestricted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("holidays: a given range is used; with no employee record the default calendar (no location)")
    void holidays_rangeAndNoEmployee() {
        LocalDate from = LocalDate.of(2027, 1, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(holidays.holidaysBetween(null, from, to)).thenReturn(List.of());

        ok(tool("core.holidays")
                .execute(
                        new AiToolContext(tenantId, userId, null),
                        parse("{\"from\":\"2027-01-01\",\"to\":\"2027-03-31\"}")));

        verify(holidays).holidaysBetween(null, from, to);
        assertThat(run("core.holidays", "{\"from\":\"2027-01-01\",\"to\":\"2026-01-01\"}")
                        .ok())
                .isFalse();
    }

    @Test
    @DisplayName("org_lookup: each kind reads its master's active list; codes and names kept, ids removed")
    void orgLookup() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(departments.list(true))
                .thenReturn(List.of(
                        new DepartmentResponse(UUID.randomUUID(), tenantId, "ENG", "Engineering", true, now, now)));
        when(designations.list(true))
                .thenReturn(List.of(
                        new DesignationResponse(UUID.randomUUID(), tenantId, "SE", "Engineer", true, now, now)));
        when(locations.list(true))
                .thenReturn(List.of(new WorkLocationResponse(
                        UUID.randomUUID(),
                        tenantId,
                        "BLR",
                        "Bengaluru",
                        "1 MG Road",
                        null,
                        "Bengaluru",
                        "Karnataka",
                        "KA",
                        "560001",
                        "IN",
                        true,
                        true,
                        now,
                        now)));

        JsonNode dept = ok(run("core.org_lookup", "{\"kind\":\"DEPARTMENT\"}"));
        JsonNode desig = ok(run("core.org_lookup", "{\"kind\":\"designation\"}"));
        JsonNode loc = ok(run("core.org_lookup", "{\"kind\":\"LOCATION\"}"));

        verify(departments).list(true);
        verify(designations).list(true);
        verify(locations).list(true);
        for (JsonNode data : List.of(dept, desig, loc)) {
            assertNoIds(data);
        }
        assertThat(dept.get(0).path("name").asText()).isEqualTo("Engineering");
        assertThat(desig.get(0).path("code").asText()).isEqualTo("SE");
        assertThat(loc.get(0).path("city").asText()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("org_lookup: kind is required and must be one of the three")
    void orgLookup_badKind() {
        assertThat(run("core.org_lookup", "{}").error()).contains("kind is required");
        assertThat(run("core.org_lookup", "{\"kind\":\"EMPLOYEE\"}").error()).contains("kind must be");
        verify(departments, org.mockito.Mockito.never()).list(anyBoolean());
        verifyNoInteractions(designations, locations);
    }

    @Test
    @DisplayName("my_wfh_requests: the caller's id; no id in the answer")
    void myWfhRequests() {
        when(wfhRequests.listEmployeeRequests(tenantId, employeeId)).thenReturn(List.of(wfhRequest()));

        JsonNode data = ok(run("core.my_wfh_requests", "{}"));

        verify(wfhRequests).listEmployeeRequests(tenantId, employeeId);
        assertNoIds(data);
        assertThat(data.get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("my_wfh_balance: the caller's id, today when no asOf, the given date otherwise; no id in the answer")
    void myWfhBalance() {
        when(wfhBalances.getBalanceSummary(any(), any(), any()))
                .thenReturn(new WfhBalanceSummaryResponse(
                        employeeId,
                        "2026",
                        new BigDecimal("24"),
                        new BigDecimal("5"),
                        BigDecimal.ONE,
                        new BigDecimal("18"),
                        new BigDecimal("4"),
                        new BigDecimal("2")));

        JsonNode data = ok(run("core.my_wfh_balance", "{}"));
        ok(run("core.my_wfh_balance", "{\"asOf\":\"2026-06-30\"}"));

        verify(wfhBalances).getBalanceSummary(tenantId, employeeId, TODAY);
        verify(wfhBalances).getBalanceSummary(tenantId, employeeId, LocalDate.of(2026, 6, 30));
        assertNoIds(data);
        assertThat(data.path("availableDays").decimalValue()).isEqualByComparingTo("18");
    }

    @Test
    @DisplayName("withoutIds: removes id, tenantId, *Id, *Ids and UUID text at any depth; keeps everything else")
    void withoutIds_nested() {
        String uuid = UUID.randomUUID().toString();
        JsonNode in = parse("{\"id\":\"x\",\"tenantId\":\"t\",\"name\":\"Asha\",\"ref\":\"" + uuid
                + "\",\"inner\":{\"approverIds\":[\"a\"],\"days\":2.5,\"list\":[\"" + uuid + "\",\"keep\"]},"
                + "\"valid\":true,\"paid\":null}");

        JsonNode out = CoreAiToolProvider.withoutIds(in);

        assertThat(out.toString())
                .isEqualTo("{\"name\":\"Asha\",\"inner\":{\"days\":2.5,\"list\":[\"keep\"]},\"valid\":true,"
                        + "\"paid\":null}");
    }

    // ---- helpers ----

    private AiTool tool(String name) {
        return provider.tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private AiToolResult run(String name, String input) {
        return tool(name).execute(caller, parse(input));
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode ok(AiToolResult result) {
        assertThat(result.ok()).as("tool result ok, error: %s", result.error()).isTrue();
        return result.data();
    }

    /** No UUID anywhere in the text, and no field named like an id at any depth. */
    private void assertNoIds(JsonNode data) {
        String text = data.toString();
        assertThat(ANY_UUID.matcher(text).find()).as("a UUID in %s", text).isFalse();
        assertThat(text).doesNotContain(tenantId.toString(), employeeId.toString(), userId.toString());
        assertNoIdFields(data);
    }

    private static void assertNoIdFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                assertThat(field.getKey())
                        .isNotEqualTo("id")
                        .doesNotEndWith("Id")
                        .doesNotEndWith("Ids");
                assertNoIdFields(field.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(CoreAiToolProviderTest::assertNoIdFields);
        }
    }

    private EmployeeResponse me() {
        return new EmployeeResponse(
                employeeId,
                tenantId,
                "E-1",
                "Asha",
                null,
                "Rao",
                null,
                LocalDate.of(2024, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                true,
                null,
                null,
                null,
                locationId,
                null,
                null,
                null,
                null);
    }

    private LeaveBalanceResponse balance() {
        return new LeaveBalanceResponse(
                employeeId,
                UUID.randomUUID(),
                "CL",
                "Casual Leave",
                "2026",
                new BigDecimal("12"),
                new BigDecimal("11"),
                BigDecimal.ZERO,
                new BigDecimal("3.5"),
                new BigDecimal("8.5"),
                null,
                null,
                false,
                BigDecimal.ONE,
                new BigDecimal("7.5"));
    }

    private LeaveRequestResponse leaveRequest() {
        Instant at = Instant.parse("2026-02-20T10:00:00Z");
        return new LeaveRequestResponse(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                "Asha Rao",
                UUID.randomUUID(),
                "Casual Leave",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 3),
                false,
                (HalfDayPeriod) null,
                new BigDecimal("2"),
                "Family function",
                LeaveRequestStatus.APPROVED,
                UUID.randomUUID(),
                false,
                at,
                List.of(UUID.randomUUID()),
                at,
                userId.toString(),
                at,
                userId.toString());
    }

    private LeaveTypeResponse leaveType() {
        UUID typeId = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        LeavePolicyResponse policy = new LeavePolicyResponse(
                UUID.randomUUID(),
                typeId,
                new BigDecimal("12"),
                true,
                null,
                BigDecimal.ONE,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                List.of(new LeavePolicyEligibilityResponse(UUID.randomUUID(), "DEPARTMENT", UUID.randomUUID())),
                at,
                at,
                List.of(overdrawn()));
        return new LeaveTypeResponse(
                typeId,
                tenantId,
                "CL",
                "Casual Leave",
                true,
                LeaveUnit.DAYS,
                true,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                policy,
                at,
                at);
    }

    private static OverdrawnEmployee overdrawn() {
        return new OverdrawnEmployee(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12"), new BigDecimal("6"), new BigDecimal("2"));
    }

    private HolidayResponse holiday() {
        return new HolidayResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Diwali",
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 8),
                false,
                null);
    }

    private WfhRequestResponse wfhRequest() {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        return new WfhRequestResponse(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                "Asha Rao",
                "E-1",
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 25),
                false,
                null,
                BigDecimal.ONE,
                "Plumber visit",
                WfhRequestStatus.PENDING,
                UUID.randomUUID(),
                false,
                null,
                at,
                at);
    }
}
