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
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * W-114.21 spec, Tests ({@code PayRunSummaryToolIT}), against real Postgres with row-level security and the real pay
 * run lifecycle: June paid, July computed. A new joiner is in July only. The tools are reached through the registry
 * the chat loop calls, then core's masking. Lives in {@code payroll}, beside the other payroll AI ITs, where the pay
 * run test schema is.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
class PayRunSummaryToolIT extends AbstractIntegrationTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PermissionService permissions;
    private EntitlementService entitlements;
    private AiToolRegistry registry;
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
        PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "EX-01", catalogue);
        PayRunTestSchema.markWorkedExampleProRata(TENANT_A);
        PayRunResponse june = payRunService.create(JUNE);
        payRunService.lock(june.id());
        worker.computeNow(june.id());
        payRunService.approve(june.id());
        payRunService.pay(june.id(), JUNE.atEndOfMonth());

        // Joins in July: in this run, not in last month's.
        UUID joiner = PayRunTestSchema.insertEmployee(TENANT_A, "EX-07", LocalDate.of(2026, 7, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, joiner);
        UUID salary = PayRunTestSchema.insertSalary(TENANT_A, joiner, LocalDate.of(2026, 7, 1));
        PayRunTestSchema.insertStructureLine(
                "employee_earning", TENANT_A, salary, catalogue.basic(), "30000.0000", null);

        july = payRunService.create(JULY);
        payRunService.lock(july.id());
        worker.computeNow(july.id());
        july = payRunService.get(july.id());
        assertThat(july.status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.clear();

        TenantClock tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(LocalDate.of(2026, 7, 28));
        permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        when(permissions.holds("payroll.run.approve")).thenReturn(false);
        entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        registry = new AiToolRegistry(
                permissions,
                entitlements,
                List.of(new PayRunAiToolProvider(payRunService, permissions, tenantClock, transactionManager)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the officer's summary of a computed run equals the run page's totals; the joiner is listed; no id")
    void summary_equalsTheRunPage() throws Exception {
        AiToolContext ctx = signIn();

        JsonNode data = call(ctx, "payroll.payrun_summary", "2026-07");

        assertThat(data.path("comparedWith").asText()).isEqualTo("2026-06");
        JsonNode thisRun = data.path("totals").path("thisRun");
        assertThat(new BigDecimal(thisRun.path("netPay").asText())).isEqualByComparingTo(july.totalNetPay());
        assertThat(new BigDecimal(thisRun.path("gross").asText())).isEqualByComparingTo(july.totalGross());
        assertThat(thisRun.path("included").asInt()).isEqualTo(july.includedCount());
        assertThat(data.path("inThisRunNotLast").path("items").toString()).contains("EX-07");
        assertThat(data.path("inThisRunNotLast").path("total").asInt()).isEqualTo(1);
        String text = data.toString();
        assertThat(ANY_UUID.matcher(text).find()).isFalse();
        assertThat(text).doesNotContain("failure_reason").doesNotContain("computation_error");
    }

    @Test
    @DisplayName("next step on the computed run, without approve: words, youCanDoThis false; the run is unchanged")
    void nextStep_andNothingChanges() throws Exception {
        AiToolContext ctx = signIn();

        JsonNode step = call(ctx, "payroll.payrun_next_step", "2026-07");
        call(ctx, "payroll.payrun_summary", "2026-07");

        assertThat(step.path("nextStep").asText()).startsWith("Review the figures");
        assertThat(step.path("youCanDoThis").asBoolean()).isFalse();
        assertThat(step.path("screenPath").asText()).isEqualTo("/payroll/runs");
        TenantContext.set(TENANT_A);
        PayRunResponse after = payRunService.get(july.id());
        assertThat(after.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(after.computedAt()).isEqualTo(july.computedAt());
    }

    @Test
    @DisplayName("without payroll.run.read neither tool is offered; without Payroll none is")
    void notOffered() {
        AiToolContext ctx = signIn();
        when(permissions.holds("payroll.run.read")).thenReturn(false);
        assertThat(names(ctx)).doesNotContain("payroll.payrun_summary", "payroll.payrun_next_step");

        when(permissions.holds("payroll.run.read")).thenReturn(true);
        assertThat(names(ctx)).contains("payroll.payrun_summary", "payroll.payrun_next_step");
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(false);
        assertThat(names(ctx)).isEmpty();
    }

    // ---- helpers ----

    private List<String> names(AiToolContext ctx) {
        return registry.toolsFor(ctx).stream().map(AiToolDescriptor::name).toList();
    }

    private JsonNode call(AiToolContext ctx, String tool, String period) throws Exception {
        AiToolResult result =
                registry.execute(tool, ctx, objectMapper.createObjectNode().put("period", period));
        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        return objectMapper.readTree(new AiMaskingFilter().mask(result.data().toString()));
    }

    private AiToolContext signIn() {
        UUID sub = UUID.randomUUID();
        TenantContext.set(TENANT_A);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sub.toString(), null, List.of()));
        return new AiToolContext(TENANT_A, sub, null);
    }
}
