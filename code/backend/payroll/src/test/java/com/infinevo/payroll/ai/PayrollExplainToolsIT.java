package com.infinevo.payroll.ai;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiMaskingFilter;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayFigureExplanationService;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.payroll.taxcalc.recalc.TaxRecalculationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-114.14 spec, Tests (integration), against real Postgres with row-level security and the real pay run lifecycle:
 * June and July paid, August only approved. Employee A has loss of pay in July and none in June; employee B's salary is
 * revised from July. Built like {@code PayrollAiToolsIT}: the registry the chat loop calls, then core's masking.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
class PayrollExplainToolsIT extends AbstractIntegrationTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private TaxSummaryService taxSummaryService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeePayRunRepository employeePayRuns;

    @Autowired
    private PayFigureExplanationService figures;

    @Autowired
    private EmployeeTdsService tdsService;

    @Autowired
    private TaxRecalculationService recalculations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiToolRegistry registry;
    private UUID employeeA;
    private UUID employeeB;
    private PayRunResponse july;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        employeeA = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "EX-01", catalogue);
        PayRunTestSchema.markWorkedExampleProRata(TENANT_A);

        // B: 40,000 Basic until June, 45,000 from July (a new salary version).
        employeeB = PayRunTestSchema.insertEmployee(TENANT_A, "EX-02", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, employeeB);
        UUID before = PayRunTestSchema.insertSalary(TENANT_A, employeeB, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine(
                "employee_earning", TENANT_A, before, catalogue.basic(), "40000.0000", null);
        UUID after = PayRunTestSchema.insertSalary(TENANT_A, employeeB, LocalDate.of(2026, 7, 1));
        PayRunTestSchema.insertStructureLine(
                "employee_earning", TENANT_A, after, catalogue.basic(), "45000.0000", null);

        // B has a tax record for 2026-27, so June, July and the approved August each deduct TDS.
        insertTaxRecord(employeeB);

        paid(JUNE);
        payInputService.record(new PayInputCommand(
                employeeA,
                JULY,
                PayInputKind.LOP_DAYS,
                new BigDecimal("2"),
                null,
                "hrms",
                "leave-" + UUID.randomUUID()));
        july = paid(JULY);
        PayRunResponse august = payRunService.create(AUGUST);
        payRunService.lock(august.id());
        worker.computeNow(august.id());
        assertThat(payRunService.approve(august.id()).status()).isEqualTo(PayRunStatus.APPROVED);
        TenantContext.clear();

        TenantClock tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(LocalDate.of(2026, 11, 24));
        when(tenantClock.dateOf(any()))
                .thenAnswer(inv -> LocalDate.ofInstant(inv.getArgument(0), TenantClock.DEFAULT_ZONE));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        PayrollAiToolProvider provider = new PayrollAiToolProvider(
                payslipService,
                taxDeclarationService,
                taxSummaryService,
                employeeService,
                tenantClock,
                transactionManager,
                employeePayRuns,
                figures,
                tdsService,
                recalculations,
                permissions);
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        registry = new AiToolRegistry(permissions, entitlements, List.of(provider));
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A: July against June - the LOP line says LOSS_OF_PAY, with the days; no id, no PF or UAN number")
    void lossOfPay() throws Exception {
        AiToolContext ctx = signIn(employeeA);
        PayslipResponse slip = payslipService.own(july.id());

        JsonNode data = explain(ctx, "payroll.explain_payslip", "2026-07");

        assertThat(data.path("comparedWith").asText()).isEqualTo("2026-06");
        assertThat(data.path("days").path("thisMonth").path("lossOfPay").asText())
                .isEqualTo("2.00");
        assertThat(data.path("days").path("lastMonth").path("lossOfPay").asText())
                .isEqualTo("0.00");
        JsonNode lopLine = null;
        for (JsonNode line : data.path("lines")) {
            if ("LOP".equals(line.path("code").asText())) {
                lopLine = line;
            }
        }
        assertThat(lopLine).as("the LOP line").isNotNull();
        assertThat(lopLine.path("reasons").toString()).contains("LOSS_OF_PAY");
        for (JsonNode line : data.path("lines")) {
            if (!line.path("code").asText().startsWith("LOP")) {
                assertThat(line.path("reasons").toString())
                        .as(line.path("code").asText())
                        .doesNotContain("PAID_DAYS_CHANGED")
                        .doesNotContain("JOINED_THIS_MONTH");
            }
        }
        String text = data.toString();
        // B's Basic (40,000, then 45,000) never reaches A's lines; A's own gross happens to be 45,000, so lines only.
        for (JsonNode line : data.path("lines")) {
            assertThat(List.of(
                            line.path("amount").asText(),
                            line.path("previousAmount").asText()))
                    .as(line.path("code").asText())
                    .doesNotContain("40000.00", "45000.00");
        }
        assertThat(ANY_UUID.matcher(text).find()).isFalse();
        if (slip.employee().uanNumber() != null) {
            assertThat(text).doesNotContain(slip.employee().uanNumber());
        }
        if (slip.employee().pfNumber() != null) {
            assertThat(text).doesNotContain(slip.employee().pfNumber());
        }
    }

    @Test
    @DisplayName(
            "B, explain_tds: deducted so far is June + July (paid), not the approved August; record labelled today")
    void deductedSoFar_paidOnly() throws Exception {
        AiToolContext ctx = signIn(employeeB);

        JsonNode tds = explain(ctx, "payroll.explain_tds", "2026-07");

        BigDecimal july = new BigDecimal(tds.path("tdsThisMonth").asText());
        BigDecimal june = new BigDecimal(tds.path("tdsLastMonth").asText());
        assertThat(july.signum()).as("July deducted TDS").isPositive();
        JsonNode year = tds.path("yearRecord");
        assertThat(year.path("asItStands").asText()).isEqualTo("today");
        assertThat(new BigDecimal(year.path("deductedSoFar").asText())).isEqualByComparingTo(june.add(july));
        assertThat(new BigDecimal(year.path("remaining").asText()))
                .isEqualByComparingTo(new BigDecimal(year.path("annualTax").asText()).subtract(june.add(july)));
        assertThat(ANY_UUID.matcher(tds.toString()).find()).isFalse();
    }

    @Test
    @DisplayName("B: the salary revision between June and July gives SALARY_REVISED; A's figures never appear")
    void salaryRevised() throws Exception {
        AiToolContext ctx = signIn(employeeB);

        JsonNode data = explain(ctx, "payroll.explain_payslip", "2026-07");

        JsonNode basic = null;
        for (JsonNode line : data.path("lines")) {
            if ("BASIC".equals(line.path("code").asText())) {
                basic = line;
            }
        }
        assertThat(basic).isNotNull();
        // Exactly the revision: a structure line is a full-month amount, so the days never add a reason.
        assertThat(basic.path("reasons").toString()).isEqualTo("[\"SALARY_REVISED\"]");
        assertThat(basic.path("difference").asText()).isEqualTo("5000.00");
        assertThat(data.toString()).doesNotContain("\"LOP\"");
    }

    @Test
    @DisplayName("August is only approved: no payslip for that month; explain_tds with no tax record says so")
    void approvedNotFoundAndNoTaxRecord() throws Exception {
        AiToolContext ctx = signIn(employeeA);

        JsonNode august = explain(ctx, "payroll.explain_payslip", "2026-08");
        assertThat(august.path("found").asBoolean(true)).isFalse();
        assertThat(august.path("message").asText()).isEqualTo("No payslip for that month yet.");

        JsonNode tds = explain(ctx, "payroll.explain_tds", "2026-07");
        assertThat(tds.path("financialYear").asText()).isEqualTo("2026-2027");
        assertThat(tds.path("noRecord").asBoolean()).isTrue();
        assertThat(tds.path("rule").asText()).isEqualTo(TdsExplanation.RULE);
        assertThat(ANY_UUID.matcher(tds.toString()).find()).isFalse();
    }

    // ---- helpers ----

    private static void insertTaxRecord(UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_tds (tenant_id, employee_id,"
                        + " financial_year, regime, source, annual_gross, annual_taxable_income, annual_tax,"
                        + " effective_from_period, is_active, created_by, updated_by) VALUES (?, ?, '2026-2027', 'NEW',"
                        + " 'OFFICER', 540000, 465000, 24000, '2026-04', true, 'it', 'it')")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }
    }

    private PayRunResponse paid(YearMonth month) {
        PayRunResponse run = payRunService.create(month);
        payRunService.lock(run.id());
        worker.computeNow(run.id());
        payRunService.approve(run.id());
        payRunService.pay(run.id(), month.atEndOfMonth());
        return run;
    }

    private JsonNode explain(AiToolContext ctx, String tool, String period) throws Exception {
        AiToolResult result =
                registry.execute(tool, ctx, objectMapper.createObjectNode().put("period", period));
        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        return objectMapper.readTree(new AiMaskingFilter().mask(result.data().toString()));
    }

    private AiToolContext signIn(UUID employeeId) {
        UUID sub = UUID.randomUUID();
        TenantContext.set(TENANT_A);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sub.toString(), null, List.of()));
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));
        return new AiToolContext(TENANT_A, sub, employeeId);
    }
}
