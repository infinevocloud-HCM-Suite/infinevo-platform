package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.payslip.PayslipController;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipNotFoundException;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.payroll.payslip.PayslipSummaryResponse;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.MyTaxDeclarationController;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.summary.MyTaxSummaryController;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.shared.authz.NoEmployeeRecordException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.7 spec, Tests: {@link PayrollAiToolProvider} - four own-data tools, each guarded as its controller is; a month
 * with no paid payslip answers the plain sentence and never calls {@code own}; "current" financial year on 31 March and
 * 1 April; a service exception becomes an error result; and, through a real {@link AiToolRegistry}, none offered or run
 * while the customer does not hold Payroll.
 */
class PayrollAiToolProviderTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ObjectMapper json = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID octoberRun = UUID.randomUUID();
    private final UUID septemberRun = UUID.randomUUID();

    private PayslipService payslips;
    private TaxDeclarationService declarations;
    private TaxSummaryService summaries;
    private EmployeeService employees;
    private TenantClock tenantClock;
    private RecordingTx readOnlyTx;
    private RecordingTx tx;
    private PayrollAiToolProvider provider;
    private AiToolContext caller;

    @BeforeEach
    void setUp() {
        payslips = mock(PayslipService.class);
        declarations = mock(TaxDeclarationService.class);
        summaries = mock(TaxSummaryService.class);
        employees = mock(EmployeeService.class);
        when(employees.currentEmployee()).thenReturn(Optional.of(employee(employeeId)));
        tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(LocalDate.of(2026, 11, 24));
        readOnlyTx = new RecordingTx();
        tx = new RecordingTx();
        provider = new PayrollAiToolProvider(payslips, declarations, summaries, employees, tenantClock, readOnlyTx, tx);
        caller = new AiToolContext(tenantId, userId, employeeId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---- the tools ----

    @Test
    @DisplayName("four tools, named and guarded as the spec's table says; all need an employee; schemas are closed")
    void fourTools_asSpecified() {
        assertThat(provider.tools())
                .extracting(AiTool::name, AiTool::requiredAction, AiTool::needsEmployee)
                .containsExactly(
                        Tuple.tuple("payroll.my_payslips", "payroll.payslip.read_own", true),
                        Tuple.tuple("payroll.my_payslip", "payroll.payslip.read_own", true),
                        Tuple.tuple("payroll.my_tax_declaration", "payroll.tax_declaration.read_own", true),
                        Tuple.tuple("payroll.my_tax_summary", "payroll.tax_declaration.read_own", true));
        for (AiTool tool : provider.tools()) {
            assertThat(tool.requiredAnyOf()).isEmpty();
            assertThat(tool.name().replace(".", "__")).hasSizeLessThanOrEqualTo(64);
            JsonNode schema = tool.inputSchema();
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
            schema.path("properties").fieldNames().forEachRemaining(name -> assertThat(name)
                    .doesNotContainIgnoringCase("employee")
                    .doesNotContainIgnoringCase("payrun")
                    .doesNotEndWith("Id")
                    .isNotEqualTo("id"));
        }
    }

    @Test
    @DisplayName("each tool's requiredAction is the @RequiresAction on the controller method it stands for")
    void requiredAction_matchesController() throws Exception {
        Map<String, String> controllerAction = Map.of(
                "payroll.my_payslips", action(PayslipController.class, "listOwn", int.class, int.class),
                "payroll.my_payslip", action(PayslipController.class, "own", UUID.class),
                "payroll.my_tax_declaration", action(MyTaxDeclarationController.class, "getOwn", String.class),
                "payroll.my_tax_summary", action(MyTaxSummaryController.class, "getOwnSummary", String.class));
        for (AiTool tool : provider.tools()) {
            assertThat(tool.requiredAction()).as(tool.name()).isEqualTo(controllerAction.get(tool.name()));
        }
    }

    @Test
    @DisplayName("my_payslips: calls listOwn(first 12), read-only; amounts are text; no id")
    void payslips_listsFirstTwelve() {
        when(payslips.listOwn(PageRequest.of(0, 12)))
                .thenReturn(new PageImpl<>(List.of(summary(octoberRun, "2026-10"), summary(septemberRun, "2026-09"))));

        AiToolResult result = run("payroll.my_payslips", "{}");

        assertThat(result.ok()).as(result.error()).isTrue();
        verify(payslips).listOwn(PageRequest.of(0, 12));
        assertThat(result.data().path("currency").asText()).isEqualTo("INR");
        assertThat(result.data().path("payslips")).hasSize(2);
        JsonNode first = result.data().path("payslips").get(0);
        assertThat(first.path("period").asText()).isEqualTo("2026-10");
        assertThat(first.path("netPay").isTextual()).isTrue();
        assertThat(first.path("netPay").asText()).isEqualTo("45000.00");
        assertNoUuid(result.data());
        assertThat(readOnlyTx.calls).isEqualTo(1);
        assertThat(tx.calls).isZero();
    }

    @Test
    @DisplayName("my_payslip with a month: finds it in the caller's own list and calls own(that run)")
    void payslip_forMonth_callsOwn() {
        when(payslips.listOwn(PageRequest.of(0, PayrollAiToolProvider.SEARCH_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of(summary(octoberRun, "2026-10"), summary(septemberRun, "2026-09"))));
        when(payslips.own(septemberRun)).thenReturn(payslip(septemberRun, "2026-09"));

        AiToolResult result = run("payroll.my_payslip", "{\"period\":\"2026-09\"}");

        assertThat(result.ok()).as(result.error()).isTrue();
        verify(payslips).own(septemberRun);
        assertThat(result.data().path("period").asText()).isEqualTo("2026-09");
        assertThat(result.data().path("netPay").asText()).isEqualTo("45000.00");
        assertThat(result.data().path("earnings").get(0).path("amount").asText())
                .isEqualTo("50000.00");
        assertNoUuid(result.data());
        assertThat(readOnlyTx.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("my_payslip with no month: the latest paid payslip")
    void payslip_noMonth_latest() {
        when(payslips.listOwn(PageRequest.of(0, PayrollAiToolProvider.SEARCH_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of(summary(octoberRun, "2026-10"), summary(septemberRun, "2026-09"))));
        when(payslips.own(octoberRun)).thenReturn(payslip(octoberRun, "2026-10"));

        AiToolResult result = run("payroll.my_payslip", "{}");

        assertThat(result.ok()).isTrue();
        verify(payslips).own(octoberRun);
        verify(payslips, never()).own(septemberRun);
        assertThat(result.data().path("period").asText()).isEqualTo("2026-10");
        assertThat(result.data().has("otherPayslipsThisMonth")).isFalse();
    }

    @Test
    @DisplayName("my_payslip for a month with a regular and an off-cycle run: the regular one is the answer,"
            + " the off-cycle one is listed under otherPayslipsThisMonth, whatever order the list gave")
    void payslip_monthWithOffCycle_regularFirst() {
        UUID bonusRun = UUID.randomUUID();
        when(payslips.listOwn(PageRequest.of(0, PayrollAiToolProvider.SEARCH_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of(
                        summary(bonusRun, "2026-10"),
                        summary(octoberRun, "2026-10"),
                        summary(septemberRun, "2026-09"))));
        when(payslips.own(bonusRun)).thenReturn(payslip(bonusRun, "2026-10", "OFF_CYCLE"));
        when(payslips.own(octoberRun)).thenReturn(payslip(octoberRun, "2026-10", "REGULAR"));

        AiToolResult result = run("payroll.my_payslip", "{\"period\":\"2026-10\"}");

        assertThat(result.ok()).as(result.error()).isTrue();
        verify(payslips).own(bonusRun);
        verify(payslips).own(octoberRun);
        verify(payslips, never()).own(septemberRun);
        assertThat(result.data().path("type").asText()).isEqualTo("REGULAR");
        assertThat(result.data().path("period").asText()).isEqualTo("2026-10");
        JsonNode others = result.data().path("otherPayslipsThisMonth");
        assertThat(others).hasSize(1);
        assertThat(others.get(0).path("type").asText()).isEqualTo("OFF_CYCLE");
        assertThat(others.get(0).path("netPay").asText()).isEqualTo("45000.00");
        assertNoUuid(result.data());
    }

    @Test
    @DisplayName("my_payslip when the month's rows straddle a page: both rows are read, none twice")
    void payslip_monthStraddlesPages() {
        int size = PayrollAiToolProvider.SEARCH_PAGE_SIZE;
        List<PayslipSummaryResponse> firstPage = new ArrayList<>();
        for (int i = 0; i < size - 1; i++) {
            firstPage.add(summary(
                    UUID.randomUUID(),
                    java.time.YearMonth.of(2026, 10).minusMonths(i).toString()));
        }
        UUID bonusRun = UUID.randomUUID();
        firstPage.add(summary(bonusRun, "2024-10"));
        UUID regularRun = UUID.randomUUID();
        when(payslips.listOwn(PageRequest.of(0, size)))
                .thenReturn(new PageImpl<>(firstPage, PageRequest.of(0, size), size + 2L));
        when(payslips.listOwn(PageRequest.of(1, size)))
                .thenReturn(new PageImpl<>(
                        List.of(
                                summary(bonusRun, "2024-10"),
                                summary(regularRun, "2024-10"),
                                summary(UUID.randomUUID(), "2024-09")),
                        PageRequest.of(1, size),
                        size + 2L));
        when(payslips.own(bonusRun)).thenReturn(payslip(bonusRun, "2024-10", "OFF_CYCLE"));
        when(payslips.own(regularRun)).thenReturn(payslip(regularRun, "2024-10", "REGULAR"));

        AiToolResult result = run("payroll.my_payslip", "{\"period\":\"2024-10\"}");

        assertThat(result.ok()).as(result.error()).isTrue();
        verify(payslips, times(1)).own(bonusRun);
        verify(payslips, times(1)).own(regularRun);
        assertThat(result.data().path("type").asText()).isEqualTo("REGULAR");
        assertThat(result.data().path("otherPayslipsThisMonth")).hasSize(1);
    }

    @Test
    @DisplayName("my_payslip with a month the caller has no paid payslip for: the plain sentence; own never called")
    void payslip_monthNotPaid_plainSentence() {
        when(payslips.listOwn(any()))
                .thenReturn(new PageImpl<>(List.of(summary(octoberRun, "2026-10"), summary(septemberRun, "2026-09"))));

        AiToolResult result = run("payroll.my_payslip", "{\"period\":\"2026-11\"}");
        AiToolResult older = run("payroll.my_payslip", "{\"period\":\"2025-03\"}");

        assertThat(result.ok()).isTrue();
        assertThat(result.data().path("message").asText()).isEqualTo("No payslip for that month yet.");
        assertThat(result.data().path("found").asBoolean(true)).isFalse();
        assertThat(older.data().path("message").asText()).isEqualTo(PayrollAiToolProvider.NO_PAYSLIP);
        verify(payslips, never()).own(any());
    }

    @Test
    @DisplayName("my_payslip reads further pages until the month is found")
    void payslip_searchesFurtherPages() {
        int size = PayrollAiToolProvider.SEARCH_PAGE_SIZE;
        List<PayslipSummaryResponse> firstPage = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            firstPage.add(summary(
                    UUID.randomUUID(),
                    java.time.YearMonth.of(2026, 10).minusMonths(i).toString()));
        }
        UUID oldRun = UUID.randomUUID();
        when(payslips.listOwn(PageRequest.of(0, size)))
                .thenReturn(new PageImpl<>(firstPage, PageRequest.of(0, size), size + 1L));
        when(payslips.listOwn(PageRequest.of(1, size)))
                .thenReturn(new PageImpl<>(List.of(summary(oldRun, "2024-10")), PageRequest.of(1, size), size + 1L));
        when(payslips.own(oldRun)).thenReturn(payslip(oldRun, "2024-10"));

        AiToolResult result = run("payroll.my_payslip", "{\"period\":\"2024-10\"}");

        assertThat(result.ok()).isTrue();
        verify(payslips).own(oldRun);
    }

    @Test
    @DisplayName("tax declaration: no year is the current one, April to March - 31 March and 1 April")
    void taxDeclaration_currentYear_onBoundaries() {
        when(declarations.find(any(), anyString())).thenReturn(Optional.of(mock(EmployeeInvestmentDeclaration.class)));
        when(declarations.readOwn(anyString())).thenAnswer(inv -> declaration(inv.getArgument(0)));

        when(tenantClock.today()).thenReturn(LocalDate.of(2027, 3, 31));
        AiToolResult march = run("payroll.my_tax_declaration", "{}");
        assertThat(march.ok()).as(march.error()).isTrue();
        verify(declarations).readOwn("2026-2027");
        assertThat(march.data().path("financialYear").asText()).isEqualTo("2026-2027");

        when(tenantClock.today()).thenReturn(LocalDate.of(2027, 4, 1));
        AiToolResult april = run("payroll.my_tax_declaration", "{}");
        verify(declarations).readOwn("2027-2028");
        assertThat(april.data().path("financialYear").asText()).isEqualTo("2027-2028");
        assertNoUuid(april.data());
        // The tax reads may write the window and summary rows, so they run in an ordinary transaction.
        assertThat(tx.calls).isEqualTo(2);
        assertThat(readOnlyTx.calls).isZero();
    }

    @Test
    @DisplayName("tax summary: a given year, long or short form, calls summaryOwn with YYYY-YYYY")
    void taxSummary_givenYear() {
        when(declarations.find(employeeId, "2025-2026"))
                .thenReturn(Optional.of(mock(EmployeeInvestmentDeclaration.class)));
        when(summaries.summaryOwn("2025-2026"))
                .thenReturn(new com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse(
                        "2025-2026", "NEW", DeclarationStatus.SUBMITTED, null, null));

        assertThat(run("payroll.my_tax_summary", "{\"financialYear\":\"2025-2026\"}")
                        .ok())
                .isTrue();
        assertThat(run("payroll.my_tax_summary", "{\"financialYear\":\"2025-26\"}")
                        .ok())
                .isTrue();
        verify(summaries, org.mockito.Mockito.times(2)).summaryOwn("2025-2026");
    }

    @Test
    @DisplayName("no declaration for the year: the plain sentence, and readOwn is never called, so no draft is started")
    void taxDeclaration_none_noDraftStarted() {
        when(declarations.find(employeeId, "2026-2027")).thenReturn(Optional.empty());

        AiToolResult declaration = run("payroll.my_tax_declaration", "{}");
        AiToolResult summary = run("payroll.my_tax_summary", "{}");

        assertThat(declaration.data().path("message").asText()).isEqualTo(PayrollAiToolProvider.NO_DECLARATION);
        assertThat(summary.data().path("message").asText()).isEqualTo(PayrollAiToolProvider.NO_DECLARATION);
        verify(declarations, never()).readOwn(anyString());
        verifyNoInteractions(summaries);
    }

    // ---- refusals ----

    @Test
    @DisplayName("an employee id or a pay run id as input is refused and runs nothing")
    void idInput_refused() {
        AiToolResult byEmployee = run("payroll.my_payslip", "{\"employeeId\":\"" + UUID.randomUUID() + "\"}");
        AiToolResult byRun = run("payroll.my_payslip", "{\"payrunId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(byEmployee.ok()).isFalse();
        assertThat(byEmployee.error()).contains("unknown input 'employeeId'");
        assertThat(byRun.error()).contains("unknown input 'payrunId'");
        verifyNoInteractions(payslips);
    }

    @Test
    @DisplayName("a bad month or financial year is refused and runs nothing")
    void badMonthOrYear_refused() {
        assertThat(run("payroll.my_payslip", "{\"period\":\"October\"}").error())
                .contains("YYYY-MM");
        assertThat(run("payroll.my_payslip", "{\"period\":\"2026-13\"}").ok()).isFalse();
        assertThat(run("payroll.my_tax_declaration", "{\"financialYear\":\"FY26\"}")
                        .error())
                .contains("YYYY-YYYY");
        assertThat(run("payroll.my_tax_summary", "{\"financialYear\":\"2025-2027\"}")
                        .error())
                .contains("two years in a row");
        verifyNoInteractions(payslips, declarations, summaries);
    }

    @Test
    @DisplayName("no employee record in the context, or the login is another employee: refused, nothing is read")
    void notTheCaller_refused() {
        AiToolContext noEmployee = new AiToolContext(tenantId, userId, null);
        for (AiTool tool : provider.tools()) {
            AiToolResult result = tool.execute(noEmployee, json.createObjectNode());
            assertThat(result.ok()).as(tool.name()).isFalse();
            assertThat(result.error()).isEqualTo(PayrollAiToolProvider.NO_EMPLOYEE);
        }
        when(employees.currentEmployee()).thenReturn(Optional.of(employee(UUID.randomUUID())));
        for (AiTool tool : provider.tools()) {
            assertThat(tool.execute(caller, json.createObjectNode()).ok())
                    .as(tool.name())
                    .isFalse();
        }
        when(employees.currentEmployee()).thenReturn(Optional.empty());
        assertThat(run("payroll.my_payslips", "{}").ok()).isFalse();
        verifyNoInteractions(payslips, declarations, summaries);
    }

    @Test
    @DisplayName("a service exception becomes an error result with one plain sentence, never an id or a stack trace")
    void serviceException_becomesError() {
        when(payslips.listOwn(PageRequest.of(0, 12)))
                .thenThrow(new IllegalStateException("connection reset at org.postgresql..."));
        AiToolResult failed = run("payroll.my_payslips", "{}");
        assertThat(failed.ok()).isFalse();
        assertThat(failed.error()).isEqualTo(PayrollAiToolProvider.SERVICE_FAILED);

        when(payslips.listOwn(PageRequest.of(0, PayrollAiToolProvider.SEARCH_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of(summary(octoberRun, "2026-10"))));
        when(payslips.own(octoberRun))
                .thenThrow(new PayslipNotFoundException("Employee " + employeeId + " not in pay run " + octoberRun));
        AiToolResult notFound = run("payroll.my_payslip", "{}");
        assertThat(notFound.error()).isEqualTo(PayrollAiToolProvider.NO_PAYSLIP);
        assertThat(ANY_UUID.matcher(notFound.error()).find()).isFalse();

        when(declarations.find(any(), anyString())).thenReturn(Optional.of(mock(EmployeeInvestmentDeclaration.class)));
        when(declarations.readOwn(anyString())).thenThrow(new NoEmployeeRecordException());
        assertThat(run("payroll.my_tax_declaration", "{}").error()).isEqualTo(PayrollAiToolProvider.NO_EMPLOYEE);

        when(summaries.summaryOwn(anyString())).thenThrow(new AccessDeniedException("no"));
        assertThat(run("payroll.my_tax_summary", "{}").error()).isEqualTo(PayrollAiToolProvider.NOT_ALLOWED);
    }

    // ---- the licence and the permission, through the real registry ----

    @Test
    @DisplayName("Payroll held: the registry offers all four; not held: none offered, and execute is refused unrun")
    void payrollLicence_enforcedByRegistry() {
        signIn();
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        AiToolRegistry registry = new AiToolRegistry(permissions, entitlements, List.of(provider));

        assertThat(registry.toolsFor(caller))
                .extracting(AiToolDescriptor::name)
                .containsExactly(
                        "payroll.my_payslips",
                        "payroll.my_payslip",
                        "payroll.my_tax_declaration",
                        "payroll.my_tax_summary");

        when(entitlements.holds(PlatformModule.PAYROLL)).thenReturn(false);

        assertThat(registry.toolsFor(caller)).isEmpty();
        for (AiTool tool : provider.tools()) {
            AiToolResult result = registry.execute(tool.name(), caller, json.createObjectNode());
            assertThat(result.ok()).as(tool.name()).isFalse();
            assertThat(result.error()).isEqualTo("Not permitted");
        }
        verifyNoInteractions(payslips, declarations, summaries);
    }

    @Test
    @DisplayName("a login without payroll.tax_declaration.read_own is not offered the two tax tools")
    void permission_filtersTools() {
        signIn();
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString()))
                .thenAnswer(inv -> !"payroll.tax_declaration.read_own".equals(inv.getArgument(0)));
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        AiToolRegistry registry = new AiToolRegistry(permissions, entitlements, List.of(provider));

        assertThat(registry.toolsFor(caller))
                .extracting(AiToolDescriptor::name)
                .containsExactly("payroll.my_payslips", "payroll.my_payslip");
    }

    // ---- helpers ----

    private void signIn() {
        TenantContext.set(tenantId);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null, List.of()));
    }

    private AiToolResult run(String name, String input) {
        AiTool tool = provider.tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
        try {
            return tool.execute(caller, json.readTree(input));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }

    private static String action(Class<?> controller, String method, Class<?>... parameters) throws Exception {
        return controller
                .getMethod(method, parameters)
                .getAnnotation(RequiresAction.class)
                .value();
    }

    private static void assertNoUuid(JsonNode data) {
        assertThat(ANY_UUID.matcher(data.toString()).find()).as(data.toString()).isFalse();
    }

    private static PayslipSummaryResponse summary(UUID run, String period) {
        return new PayslipSummaryResponse(run, period, LocalDate.parse(period + "-28"), new BigDecimal("45000"));
    }

    private PayslipResponse payslip(UUID run, String period) {
        return payslip(run, period, "REGULAR");
    }

    private PayslipResponse payslip(UUID run, String period, String type) {
        return new PayslipResponse(
                new PayslipResponse.RunSummary(
                        run, type, period, LocalDate.parse(period + "-28"), LocalDate.parse(period + "-28"), "PAID"),
                new PayslipResponse.EmployerSummary("Acme"),
                new PayslipResponse.EmployeeSummary(
                        employeeId, "EMP-01", "Asha Rao", null, null, LocalDate.of(2024, 1, 1), "ACTIVE", null, null),
                new PayslipResponse.DaysSummary(
                        new BigDecimal("30.00"), new BigDecimal("30.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(new PayslipLineResponse("BASIC", "Basic", new BigDecimal("50000.00"), "STRUCTURE", true)),
                List.of(new PayslipLineResponse("PF", "Provident Fund", new BigDecimal("5000.00"), "STATUTORY", false)),
                List.of(),
                List.of(),
                new PayslipResponse.TotalsSummary(
                        new BigDecimal("50000.00"),
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("45000.00")),
                null);
    }

    private TaxDeclarationResponse declaration(String fy) {
        return new TaxDeclarationResponse(
                UUID.randomUUID(),
                employeeId,
                "Asha Rao",
                fy,
                "NEW",
                DeclarationStatus.DRAFT,
                true,
                false,
                false,
                false,
                null,
                null,
                Instant.parse("2026-11-20T10:00:00Z"),
                true,
                true,
                true,
                new BigDecimal("100000"));
    }

    private EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                tenantId,
                "EMP-001",
                "Test",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp@payroll.test",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    /** Runs the callback with no transaction and counts the calls, so a test can tell which template ran. */
    private static final class RecordingTx implements TransactionOperations {
        int calls;

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            calls++;
            return TransactionOperations.withoutTransaction().execute(action);
        }
    }
}
