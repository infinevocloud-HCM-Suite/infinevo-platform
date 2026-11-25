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
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * W-114.7 spec, Tests (integration), against real Postgres with row-level security: the Payroll tools run through the
 * real {@link AiToolRegistry}, the real payslip service and the real pay run lifecycle. Employee A asking for July gets
 * A's paid payslip and not B's; August, only APPROVED, is not found; a login without {@code payroll.payslip.read_own}
 * is not offered the payslip tools; a customer without Payroll is offered none and cannot run one.
 *
 * <p>The spec places this test in {@code app} with the stub model scripted to call the tool. This task was confined to
 * {@code payroll}, so it calls the registry the chat loop calls ({@code AiChatServiceImpl}) directly instead, and runs
 * the result through core's {@link AiMaskingFilter} as that loop does. {@code PayrollTestApp} has no permission cache
 * or entitlement source, so the registry is given a {@link PermissionService} and {@link EntitlementService} that
 * answer from the maps below, keyed on the bound login and tenant as the real ones are.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
class PayrollAiToolsIT extends AbstractIntegrationTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final List<String> ALL_ACTIONS =
            List.of("payroll.payslip.read_own", "payroll.tax_declaration.read_own");

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private TaxSummaryService taxSummaryService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, List<String>> actionsBySubject = new ConcurrentHashMap<>();
    private final Set<UUID> payrollTenants = ConcurrentHashMap.newKeySet();

    private AiToolRegistry registry;
    private UUID employeeA;
    private UUID employeeB;
    private UUID subA;
    private UUID subB;
    private UUID subNoPayslip;
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
        employeeA = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "AI-01", catalogue);
        // B's salary differs from A's (a higher Basic, no meal card, bonus or reimbursement), so the two payslips can
        // be told apart by their figures.
        employeeB = PayRunTestSchema.insertEmployee(TENANT_A, "AI-02", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, employeeB);
        UUID ctcB = PayRunTestSchema.insertSalary(TENANT_A, employeeB, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctcB, catalogue.basic(), "40000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctcB, catalogue.hra(), "10000.0000", null);

        // July: computed, approved and paid. August: computed and approved, never paid.
        july = payRunService.create(JULY);
        payRunService.lock(july.id());
        worker.computeNow(july.id());
        payRunService.approve(july.id());
        payRunService.pay(july.id(), LocalDate.of(2026, 7, 31));
        PayRunResponse august = payRunService.create(AUGUST);
        payRunService.lock(august.id());
        worker.computeNow(august.id());
        assertThat(payRunService.approve(august.id()).status()).isEqualTo(PayRunStatus.APPROVED);
        TenantContext.clear();

        subA = UUID.randomUUID();
        subB = UUID.randomUUID();
        subNoPayslip = UUID.randomUUID();
        actionsBySubject.put(subA, ALL_ACTIONS);
        actionsBySubject.put(subB, ALL_ACTIONS);
        actionsBySubject.put(subNoPayslip, List.of("payroll.tax_declaration.read_own"));
        payrollTenants.add(TENANT_A);

        TenantClock tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(LocalDate.of(2026, 11, 24));
        PayrollAiToolProvider provider = new PayrollAiToolProvider(
                payslipService,
                taxDeclarationService,
                taxSummaryService,
                employeeService,
                tenantClock,
                transactionManager);
        registry = new AiToolRegistry(permissionsOfBoundLogin(), entitlementsOfBoundTenant(), List.of(provider));
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("employee A asks for July and gets A's paid payslip, amounts as two-decimal text, no id")
    void employeeA_getsOwnPaidPayslip() throws Exception {
        AiToolContext ctx = signIn(subA, employeeA);
        PayslipResponse expected = payslipService.own(july.id());

        AiToolResult result = registry.execute(
                "payroll.my_payslip", ctx, objectMapper.createObjectNode().put("period", "2026-07"));

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        JsonNode data = result.data();
        assertThat(data.path("period").asText()).isEqualTo("2026-07");
        assertThat(data.path("paidOn").asText()).isEqualTo("2026-07-31");
        assertThat(data.path("currency").asText()).isEqualTo("INR");
        assertThat(data.path("netPay").asText())
                .isEqualTo(expected.totals().netPay().toPlainString())
                .matches("^-?[0-9]+\\.[0-9]{2}$");
        assertThat(data.path("grossEarnings").asText())
                .isEqualTo(expected.totals().grossEarnings().toPlainString());
        assertThat(data.path("earnings")).isNotEmpty();
        for (JsonNode line : data.path("earnings")) {
            assertThat(line.path("amount").isTextual()).isTrue();
            assertThat(line.path("amount").asText()).matches("^-?[0-9]+\\.[0-9]{2}$");
        }
        assertThat(ANY_UUID.matcher(data.toString()).find()).as(data.toString()).isFalse();
        assertThat(data.toString()).doesNotContain("AI-01");

        // What the chat loop stores and sends: the masker leaves a payslip without identifiers as it is.
        assertThat(new AiMaskingFilter().mask(data)).isEqualTo(data);

        // And B, asking the same, gets B's July, not A's.
        AiToolContext ctxB = signIn(subB, employeeB);
        AiToolResult resultB = registry.execute(
                "payroll.my_payslip", ctxB, objectMapper.createObjectNode().put("period", "2026-07"));
        assertThat(resultB.ok()).as(String.valueOf(resultB.error())).isTrue();
        assertThat(resultB.data().path("grossEarnings").asText())
                .isNotEqualTo(data.path("grossEarnings").asText());
    }

    @Test
    @DisplayName("August, only APPROVED, is not found: the plain sentence; the list shows July alone")
    void approvedRun_notFound() {
        AiToolContext ctx = signIn(subA, employeeA);

        AiToolResult august = registry.execute(
                "payroll.my_payslip", ctx, objectMapper.createObjectNode().put("period", "2026-08"));
        assertThat(august.ok()).isTrue();
        assertThat(august.data().path("message").asText()).isEqualTo("No payslip for that month yet.");

        AiToolResult latest = registry.execute("payroll.my_payslip", ctx, objectMapper.createObjectNode());
        assertThat(latest.data().path("period").asText()).isEqualTo("2026-07");

        AiToolResult list = registry.execute("payroll.my_payslips", ctx, objectMapper.createObjectNode());
        assertThat(list.ok()).as(String.valueOf(list.error())).isTrue();
        assertThat(list.data().path("payslips")).hasSize(1);
        assertThat(list.data().path("payslips").get(0).path("period").asText()).isEqualTo("2026-07");
    }

    @Test
    @DisplayName("a context naming B while A is signed in runs nothing")
    void contextForAnotherEmployee_refused() {
        signIn(subA, employeeA);
        AiToolContext forged = new AiToolContext(TENANT_A, subA, employeeB);

        AiToolResult result = registry.execute(
                "payroll.my_payslip", forged, objectMapper.createObjectNode().put("period", "2026-07"));

        assertThat(result.ok()).isFalse();
        assertThat(result.data()).isNull();
    }

    @Test
    @DisplayName("a login without payroll.payslip.read_own is not offered the payslip tools, and cannot run them")
    void withoutPayslipReadOwn_notOffered() {
        AiToolContext ctx = signIn(subNoPayslip, employeeA);

        assertThat(registry.toolsFor(ctx))
                .extracting(AiToolDescriptor::name)
                .containsExactly("payroll.my_tax_declaration", "payroll.my_tax_summary");
        AiToolResult refused = registry.execute(
                "payroll.my_payslip", ctx, objectMapper.createObjectNode().put("period", "2026-07"));
        assertThat(refused.ok()).isFalse();
        assertThat(refused.error()).isEqualTo("Not permitted");
    }

    @Test
    @DisplayName("Payroll held: all four offered; a customer without Payroll is offered none and cannot run one")
    void withoutPayroll_noneOffered() {
        AiToolContext ctx = signIn(subA, employeeA);
        assertThat(registry.toolsFor(ctx)).hasSize(4);

        payrollTenants.remove(TENANT_A);

        assertThat(registry.toolsFor(ctx)).isEmpty();
        AiToolResult refused = registry.execute(
                "payroll.my_payslip", ctx, objectMapper.createObjectNode().put("period", "2026-07"));
        assertThat(refused.ok()).isFalse();
        assertThat(refused.error()).isEqualTo("Not permitted");
    }

    // ---- helpers ----

    /** Binds the tenant and the login as the chat request does, and the login's employee record. */
    private AiToolContext signIn(UUID sub, UUID employeeId) {
        TenantContext.set(TENANT_A);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sub.toString(), null, List.of()));
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));
        return new AiToolContext(TENANT_A, sub, employeeId);
    }

    private PermissionService permissionsOfBoundLogin() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenAnswer(inv -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return false;
            }
            List<String> held = actionsBySubject.get(UUID.fromString(auth.getName()));
            return held != null && held.contains(inv.<String>getArgument(0));
        });
        return permissions;
    }

    private EntitlementService entitlementsOfBoundTenant() {
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class)))
                .thenAnswer(inv -> inv.getArgument(0) == PlatformModule.PAYROLL
                        && TenantContext.current().map(payrollTenants::contains).orElse(false));
        return entitlements;
    }
}
