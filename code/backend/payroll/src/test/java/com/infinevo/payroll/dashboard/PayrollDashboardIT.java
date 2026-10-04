package com.infinevo.payroll.dashboard;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-37 §7 — the acceptance test. Runs, rows and lines are written as {@code migration_user}; the
 * dashboard reads them through the real service as {@code app_user}, so row-level security applies.
 * The statutory lines are hand-inserted {@code STATUTORY} rows, proving the EPF sum without W-31.4's
 * contributor. The controller sits behind the real {@link RequiresActionAspect}; whether a role holds
 * {@code payroll.run.read} is read from the seeded {@code core.role_action} grants, not assumed.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class PayrollDashboardIT extends AbstractIntegrationTest {

    private static final String READ = "payroll.run.read";

    /** A tenant inserted here, so its system roles come from the provisioning trigger alone. */
    private static final UUID ROLE_TENANT = UUID.randomUUID();

    @Autowired
    private PayrollDashboardService dashboardService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private final AtomicReference<String> role = new AtomicReference<>();

    private MockMvc mvc;
    private UUID april;
    private UUID may;
    private UUID june;
    private UUID otherTenantRun;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, 'Dashboard roles')")) {
            ps.setObject(1, ROLE_TENANT);
            ps.executeUpdate();
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();

        UUID paid = PayRunTestSchema.insertEmployee(TENANT_A, "D-01", LocalDate.of(2025, 1, 1), "ACTIVE", null);
        UUID unbanked = PayRunTestSchema.insertEmployee(TENANT_A, "D-02", LocalDate.of(2025, 1, 1), "ACTIVE", null);
        UUID other = PayRunTestSchema.insertEmployee(TENANT_B, "D-B1", LocalDate.of(2025, 1, 1), "ACTIVE", null);

        // April: PAID, one employee, every EPF code, PT, TDS, an unknown statutory code and a structure line.
        april = insertRun(TENANT_A, "2026-04", "PAID", "100000.0000", "12000.0000", "88000.0000", 1, 0);
        UUID aprilRow = insertRow(TENANT_A, april, paid, null);
        insertLine(TENANT_A, aprilRow, april, "EARNING", "STRUCTURE", "BASIC", "100000.0000");
        insertLine(TENANT_A, aprilRow, april, "DEDUCTION", "STATUTORY", "EPF_EMPLOYEE", "1800.0000");
        insertLine(TENANT_A, aprilRow, april, "BENEFIT", "STATUTORY", "EPF_EMPLOYER", "550.0000");
        insertLine(TENANT_A, aprilRow, april, "BENEFIT", "STATUTORY", "EPS_EMPLOYER", "1250.0000");
        insertLine(TENANT_A, aprilRow, april, "BENEFIT", "STATUTORY", "EDLI", "75.0000");
        insertLine(TENANT_A, aprilRow, april, "BENEFIT", "STATUTORY", "EPF_ADMIN", "75.0000");
        insertLine(TENANT_A, aprilRow, april, "DEDUCTION", "STATUTORY", "PROFESSIONAL_TAX", "200.0000");
        insertLine(TENANT_A, aprilRow, april, "DEDUCTION", "STATUTORY", "LWF_EMPLOYEE", "25.0000");
        insertLine(TENANT_A, aprilRow, april, "DEDUCTION", "TAX", "TDS", "10000.0000");

        // May: COMPUTED, one included and one skipped for no bank details. Its lines are not paid.
        may = insertRun(TENANT_A, "2026-05", "COMPUTED", "105000.0000", "13000.0000", "92000.0000", 1, 1);
        UUID mayRow = insertRow(TENANT_A, may, paid, null);
        insertRow(TENANT_A, may, unbanked, "NO_BANK_DETAILS");
        insertLine(TENANT_A, mayRow, may, "DEDUCTION", "STATUTORY", "EPF_EMPLOYEE", "1800.0000");
        insertLine(TENANT_A, mayRow, may, "DEDUCTION", "TAX", "TDS", "11000.0000");

        // June: CANCELLED — the newest period, and in neither recent_runs nor months.
        june = insertRun(TENANT_A, "2026-06", "CANCELLED", "0.0000", "0.0000", "0.0000", 0, 0);

        // Tenant B: a newer PAID run with lines that must never reach tenant A's dashboard.
        otherTenantRun = insertRun(TENANT_B, "2026-08", "PAID", "999999.0000", "99999.0000", "900000.0000", 1, 0);
        UUID otherRow = insertRow(TENANT_B, otherTenantRun, other, null);
        insertLine(TENANT_B, otherRow, otherTenantRun, "DEDUCTION", "STATUTORY", "EPF_EMPLOYEE", "5000.0000");
        insertLine(TENANT_B, otherRow, otherTenantRun, "DEDUCTION", "TAX", "TDS", "7777.0000");

        TenantContext.set(TENANT_A);
        mvc = mockMvc();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Acceptance: current run is the newest, two months, PAID-only totals, NO_BANK_DETAILS = 1, EPF summed")
    void acceptance() throws Exception {
        role.set("payroll-officer");
        JsonNode data = getData("/api/v1/payroll/dashboard?fy=2026");

        assertThat(data.at("/financial_year/start").asText()).isEqualTo("2026-04-01");
        assertThat(data.at("/financial_year/end").asText()).isEqualTo("2027-03-31");

        assertThat(data.at("/current_run/payrun_id").asText()).isEqualTo(may.toString());
        assertThat(data.at("/current_run/status").asText()).isEqualTo("COMPUTED");
        assertThat(data.at("/current_run/progress_done").isNull()).isTrue();
        assertThat(data.at("/current_run/gross").decimalValue()).isEqualByComparingTo("105000.00");
        assertThat(data.at("/recent_runs")).hasSize(2);
        assertThat(data.at("/recent_runs/1/payrun_id").asText()).isEqualTo(april.toString());
        assertThat(data.at("/recent_runs/1/paid_on").asText()).isEqualTo("2026-04-30");

        assertThat(data.at("/employees/active_today").asInt()).isEqualTo(2);
        assertThat(data.at("/employees/as_at_run/payrun_id").asText()).isEqualTo(may.toString());
        assertThat(data.at("/employees/as_at_run/included").asInt()).isEqualTo(1);
        assertThat(data.at("/employees/as_at_run/skipped").asInt()).isEqualTo(1);
        assertThat(data.at("/employees/as_at_run/skipped_by_reason/NO_BANK_DETAILS")
                        .asLong())
                .isEqualTo(1);
        assertThat(data.at("/employees/as_at_run/skipped_by_reason/NO_SALARY").asLong())
                .isZero();

        assertThat(data.at("/months")).hasSize(2);
        assertThat(data.at("/months/0/period").asText()).isEqualTo("2026-04");
        assertThat(data.at("/months/0/status").asText()).isEqualTo("PAID");
        assertThat(data.at("/months/0/tax").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(data.at("/months/1/period").asText()).isEqualTo("2026-05");
        assertThat(data.at("/months/1/tax").decimalValue()).isEqualByComparingTo("11000.00");

        assertThat(data.at("/year_totals/paid_runs").asInt()).isEqualTo(1);
        assertThat(data.at("/year_totals/gross").decimalValue()).isEqualByComparingTo("100000.00");
        assertThat(data.at("/year_totals/deductions").decimalValue()).isEqualByComparingTo("12000.00");
        assertThat(data.at("/year_totals/tax").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(data.at("/year_totals/net_pay").decimalValue()).isEqualByComparingTo("88000.00");
        assertThat(data.at("/year_totals/gross").decimalValue().scale()).isEqualTo(2);

        assertThat(data.at("/statutory/epf/employee").decimalValue()).isEqualByComparingTo("1800.00");
        assertThat(data.at("/statutory/epf/employer").decimalValue()).isEqualByComparingTo("1950.00");
        assertThat(data.at("/statutory/esi/employee").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.at("/statutory/esi/employer").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.at("/statutory/professional_tax/employee").decimalValue())
                .isEqualByComparingTo("200.00");
        assertThat(data.at("/statutory/professional_tax/employer").isMissingNode())
                .isTrue();
        assertThat(data.at("/statutory/tds/employee").decimalValue()).isEqualByComparingTo("10000.00");

        assertThat(data.toString()).doesNotContain(otherTenantRun.toString());
    }

    @Test
    @DisplayName("A cancelled run is in neither recent_runs nor months")
    void cancelledRunExcluded() throws Exception {
        role.set("payroll-officer");
        JsonNode data = getData("/api/v1/payroll/dashboard?fy=2026");

        assertThat(data.toString()).doesNotContain(june.toString());
        assertThat(data.at("/recent_runs").findValuesAsText("status")).doesNotContain("CANCELLED");
        assertThat(data.at("/months").findValuesAsText("period")).doesNotContain("2026-06");
    }

    @Test
    @DisplayName("Cross-tenant: tenant B sees only its own run and figures, never tenant A's")
    void secondTenantIsolated() {
        TenantContext.set(TENANT_B);
        PayrollDashboardResponse b = dashboardService.summary(2026);

        assertThat(b.currentRun().payrunId()).isEqualTo(otherTenantRun);
        assertThat(b.recentRuns())
                .extracting(PayrollDashboardResponse.RunCard::payrunId)
                .containsExactly(otherTenantRun);
        assertThat(b.months())
                .extracting(PayrollDashboardResponse.MonthRow::payrunId)
                .containsExactly(otherTenantRun);
        assertThat(b.statutory().epf().employee()).isEqualByComparingTo("5000.00");
        assertThat(b.statutory().tds().employee()).isEqualByComparingTo("7777.00");
        assertThat(b.employees().activeToday()).isEqualTo(1);

        TenantContext.set(TENANT_A);
        PayrollDashboardResponse a = dashboardService.summary(2026);
        assertThat(a.recentRuns())
                .extracting(PayrollDashboardResponse.RunCard::payrunId)
                .doesNotContain(otherTenantRun);
        assertThat(a.statutory().epf().employee()).isEqualByComparingTo("1800.00");
    }

    @Test
    @DisplayName("finance holds payroll.run.read and gets 200; employee does not and gets 403")
    void financeAllowedEmployeeForbidden() throws Exception {
        role.set("finance");
        mvc.perform(get("/api/v1/payroll/dashboard").param("fy", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        role.set("employee");
        mvc.perform(get("/api/v1/payroll/dashboard").param("fy", "2026"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("fy=2101 and fy=1999 are 400 with the validation envelope; a year in range with no runs is zeros")
    void yearOutOfRange() throws Exception {
        role.set("payroll-officer");
        mvc.perform(get("/api/v1/payroll/dashboard").param("fy", "2101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/payroll/dashboard").param("fy", "1999")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payroll/dashboard").param("fy", "abcd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        JsonNode empty = getData("/api/v1/payroll/dashboard?fy=2030");
        assertThat(empty.at("/months")).isEmpty();
        assertThat(empty.at("/year_totals/paid_runs").asInt()).isZero();
        assertThat(empty.at("/statutory/epf/employee").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("No fy: the financial year containing today")
    void defaultYearContainsToday() throws Exception {
        role.set("payroll-officer");
        JsonNode data = getData("/api/v1/payroll/dashboard");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        int startYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        assertThat(data.at("/financial_year/start").asText())
                .isEqualTo(LocalDate.of(startYear, 4, 1).toString());
    }

    private JsonNode getData(String url) throws Exception {
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(body).get("data");
    }

    /** The controller behind the real aspect; {@code holds} answers from the current role's seeded grants. */
    private MockMvc mockMvc() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenAnswer(inv -> grants(role.get(), inv.getArgument(0)) > 0);
        doAnswer(inv -> {
                    String action = inv.getArgument(0);
                    if (!permissions.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissions)
                .require(any());

        AspectJProxyFactory factory = new AspectJProxyFactory(new PayrollDashboardController(dashboardService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissions));
        PayrollDashboardController controller = factory.getProxy();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private static int grants(String roleCode, String actionCode) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.role_action ra "
                        + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                        + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code = ?")) {
            ps.setObject(1, ROLE_TENANT);
            ps.setString(2, roleCode);
            ps.setString(3, actionCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static UUID insertRun(
            UUID tenantId,
            String period,
            String status,
            String gross,
            String deductions,
            String net,
            int included,
            int skipped)
            throws SQLException {
        YearMonth month = YearMonth.parse(period);
        LocalDate end = month.atEndOfMonth();
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.payrun (
                            id, tenant_id, period, period_start, period_end, cutoff_date, pay_date, paid_on,
                            run_type, status, included_count, skipped_count, total_gross, total_deductions,
                            total_net_pay, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'REGULAR', ?, ?, ?, ?, ?, ?, 'seed', 'seed')
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, period);
            ps.setObject(4, month.atDay(1));
            ps.setObject(5, end);
            ps.setObject(6, end.minusDays(5));
            ps.setObject(7, end);
            ps.setObject(8, PayRunStatus.PAID.name().equals(status) ? end : null);
            ps.setString(9, status);
            ps.setInt(10, included);
            ps.setInt(11, skipped);
            ps.setBigDecimal(12, new BigDecimal(gross));
            ps.setBigDecimal(13, new BigDecimal(deductions));
            ps.setBigDecimal(14, new BigDecimal(net));
            ps.executeUpdate();
        }
        return id;
    }

    /** An employee row; {@code skipReason} null for INCLUDED, set for SKIPPED. */
    private static UUID insertRow(UUID tenantId, UUID payrunId, UUID employeeId, String skipReason)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_payrun (
                            id, tenant_id, payrun_id, employee_id, inclusion_status, skip_reason, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, 'seed', 'seed')
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, payrunId);
            ps.setObject(4, employeeId);
            ps.setString(5, skipReason == null ? "INCLUDED" : "SKIPPED");
            ps.setString(6, skipReason);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertLine(
            UUID tenantId, UUID rowId, UUID payrunId, String kind, String source, String code, String amount)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_payrun_line (
                            id, tenant_id, employee_payrun_id, payrun_id, line_kind, source,
                            component_code, component_name, amount, is_taxable, sort_order, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, 1, 'seed', 'seed')
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, rowId);
            ps.setObject(4, payrunId);
            ps.setString(5, kind);
            ps.setString(6, source);
            ps.setString(7, code);
            ps.setString(8, code);
            ps.setBigDecimal(9, new BigDecimal(amount));
            ps.executeUpdate();
        }
    }
}
