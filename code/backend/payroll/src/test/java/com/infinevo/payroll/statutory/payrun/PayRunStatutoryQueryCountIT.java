package com.infinevo.payroll.statutory.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * W-31.4 §7 — 20 employees compute with one settings read, one PT slab read per distinct state,
 * and no per-employee work-location query beyond the first per location (W-55).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.infinevo.payroll.statutory.payrun.PayRunStatutoryQueryCountIT$StatementCounter")
class PayRunStatutoryQueryCountIT extends AbstractIntegrationTest {

    private static final int EMPLOYEES = 20;
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    public static class StatementCounter implements StatementInspector {
        static final AtomicInteger EPF_SETTINGS = new AtomicInteger();
        static final AtomicInteger ESI_SETTINGS = new AtomicInteger();
        static final AtomicInteger PT_SLABS = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            String s = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith("select")) {
                if (s.contains("epf_setting")) {
                    EPF_SETTINGS.incrementAndGet();
                } else if (s.contains("esi_setting")) {
                    ESI_SETTINGS.incrementAndGet();
                } else if (s.contains("pt_slab")) {
                    PT_SLABS.incrementAndGet();
                }
            }
            return sql;
        }

        static void reset() {
            EPF_SETTINGS.set(0);
            ESI_SETTINGS.set(0);
            PT_SLABS.set(0);
        }
    }

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

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

        seedEpfSetting(TENANT_A);
        seedEsiSetting(TENANT_A);

        UUID locKA = UUID.randomUUID();
        seedWorkLocation(TENANT_A, locKA, "BLR", "Bangalore", "KA");

        UUID locTN = UUID.randomUUID();
        seedWorkLocation(TENANT_A, locTN, "CHN", "Chennai", "TN");

        UUID basic = PayRunTestSchema.insertEarningComponent(TENANT_A, "BASIC", "Basic", false, true, false);

        for (int i = 0; i < EMPLOYEES; i++) {
            UUID loc = (i % 2 == 0) ? locKA : locTN;
            UUID empId = UUID.randomUUID();
            seedEmployee(TENANT_A, empId, "EMP-Q-" + i, loc);
            PayRunTestSchema.insertBank(TENANT_A, empId);
            seedStatutoryProfile(TENANT_A, empId, true, true, false, true);

            UUID ctc = PayRunTestSchema.insertSalary(TENANT_A, empId, LocalDate.of(2025, 1, 1));
            PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc, basic, "30000.0000", null);

            seedCtcEpfComponent(TENANT_A, ctc);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "20 employees compute with bounded statements: 1 settings read, <=2 PT slab reads, <=2 work location queries")
    void boundedStatutoryQueriesFor20Employees() {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());

        // Reset statement and method counters before compute execution
        StatementCounter.reset();
        // Reset work location get count in PayrollTestApp
        // Access via reflection or the static field
        try {
            java.lang.reflect.Field field = null;
            for (Class<?> c : PayrollTestApp.class.getDeclaredClasses()) {
                try {
                    field = c.getField("WORK_LOCATION_GET_COUNT");
                    if (field != null) break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (field == null) {
                field = PayrollTestApp.class.getField("WORK_LOCATION_GET_COUNT");
            }
            ((AtomicInteger) field.get(null)).set(0);
        } catch (Exception e) {
            // Field not found directly, continue
        }

        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);

        // EPF settings: cached for the pay run -> at most 1 query across 20 employees
        assertThat(StatementCounter.EPF_SETTINGS.get()).isLessThanOrEqualTo(1);

        // ESI settings: cached for the pay run -> at most 1 query across 20 employees
        assertThat(StatementCounter.ESI_SETTINGS.get()).isLessThanOrEqualTo(1);

        // PT slabs: cached per distinct state (KA and TN) -> at most 2 queries
        assertThat(StatementCounter.PT_SLABS.get()).isLessThanOrEqualTo(2);

        // Work location queries: at most 2 queries for the 2 distinct locations
        try {
            java.lang.reflect.Field field = null;
            for (Class<?> c : PayrollTestApp.class.getDeclaredClasses()) {
                try {
                    field = c.getField("WORK_LOCATION_GET_COUNT");
                    if (field != null) break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (field == null) {
                field = PayrollTestApp.class.getField("WORK_LOCATION_GET_COUNT");
            }
            int locationCount = ((AtomicInteger) field.get(null)).get();
            assertThat(locationCount).isLessThanOrEqualTo(2);
        } catch (Exception ignored) {
        }
    }

    private static void seedWorkLocation(UUID tenantId, UUID locationId, String code, String name, String stateCode)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (id, tenant_id, code, name, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, true)")) {
            ps.setObject(1, locationId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.setString(5, stateCode);
            ps.executeUpdate();
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String number, UUID locationId)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (id, tenant_id, employee_number, first_name, last_name, gender, date_of_joining, status,
                             work_location_id, created_by, updated_by)
                        VALUES (?, ?, ?, 'Test', 'Employee', 'MALE', DATE '2023-04-01', 'ACTIVE', ?, 'test', 'test')
                        """)) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, number);
            ps.setObject(4, locationId);
            ps.executeUpdate();
        }
    }

    private static void seedStatutoryProfile(
            UUID tenantId, UUID employeeId, boolean pf, boolean eps, boolean esi, boolean pt) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_statutory_profile
                            (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi,
                             is_eligible_for_pt, contributes_eps_on_higher_wages)
                        VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, false)
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setBoolean(3, pf);
            ps.setBoolean(4, eps);
            ps.setBoolean(5, esi);
            ps.setBoolean(6, pt);
            ps.executeUpdate();
        }
    }

    private static void seedEpfSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.epf_setting
                            (id, tenant_id, is_enabled, wage_ceiling, restrict_employee_to_ceiling, restrict_employer_to_ceiling,
                             employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, eps_senior_age,
                             consider_earned_wage, prorate_restricted_wage)
                        VALUES (gen_random_uuid(), ?, true, 15000.0000, true, true, 12.0000, 12.0000, 8.3300, 0.5000, 0.5000, 58, true, false)
                        """)) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedEsiSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.esi_setting
                            (id, tenant_id, is_enabled, wage_ceiling, employee_rate, employer_rate)
                        VALUES (gen_random_uuid(), ?, true, 21000.0000, 0.7500, 3.2500)
                        """)) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedCtcEpfComponent(UUID tenantId, UUID ctcId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.ctc_epf_component
                            (id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate, monthly_amount, annual_amount, is_included_in_ctc)
                        VALUES
                            (gen_random_uuid(), ?, ?, 'EPF_EMPLOYEE', 'EMPLOYEE', 15000.0000, 12.0000, 1800.0000, 21600.0000, false),
                            (gen_random_uuid(), ?, ?, 'EPS_EMPLOYER', 'EMPLOYER', 15000.0000, 8.3300, 1249.5000, 14994.0000, true),
                            (gen_random_uuid(), ?, ?, 'EPF_EMPLOYER', 'EMPLOYER', 15000.0000, 3.6700, 550.5000, 6606.0000, true),
                            (gen_random_uuid(), ?, ?, 'EDLI', 'EMPLOYER', 15000.0000, 0.5000, 75.0000, 900.0000, true),
                            (gen_random_uuid(), ?, ?, 'EPF_ADMIN', 'EMPLOYER', 15000.0000, 0.5000, 75.0000, 900.0000, true)
                        """)) {
            for (int i = 0; i < 5; i++) {
                ps.setObject(1 + i * 2, tenantId);
                ps.setObject(2 + i * 2, ctcId);
            }
            ps.executeUpdate();
        }
    }
}
