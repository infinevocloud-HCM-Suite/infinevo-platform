package com.infinevo.payroll.statutory.lines;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Acceptance integration test for statutory EPF & ESI lines on salary versions (W-31.3).
 * Spec §7: create a version: seven rows exist and statutory[] returns them;
 * revise: the new version has its own rows from the rates in force, old version's rows unchanged;
 * update a future version: rows rewritten, count unchanged.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class SalaryStatutoryLinesIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    private UUID employeeId;
    private UUID basicEarningId;
    private UUID hraEarningId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-001", "Alice", "Smith");
        seedEmployeePersonal(TENANT_A, employeeId, LocalDate.of(1996, 1, 1));
        seedStatutoryProfile(TENANT_A, employeeId, true, true, true);
        seedEpfSetting(TENANT_A, new BigDecimal("12.0000"), new BigDecimal("0.5000"));
        seedEsiSetting(TENANT_A);

        TenantContext.set(TENANT_A);
        Earning basic = new Earning(TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic Salary");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic = earningRepository.save(basic);
        basicEarningId = basic.getId();

        Earning hra = new Earning(TENANT_A, "system");
        hra.setCode("HRA");
        hra.setName("House Rent Allowance");
        hra.setEarningType("ALLOWANCE");
        hra.setCalculationType(CalculationType.FLAT);
        hra.setIncludedInCtc(true);
        hra = earningRepository.save(hra);
        hraEarningId = hra.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create version: seven rows exist and statutory[] returns them")
    void createVersion_sevenRowsExistAndReturnedInStatutory() throws SQLException {
        TenantContext.set(TENANT_A);

        // Basic 10,000, HRA 5,000 -> Gross 15,000.
        // Employer statutory monthly: EPS 833, EPF 367, EDLI 50, Admin 50, ESI 487.50 -> 1,787.50 * 12 = 21,450.
        // Annual CTC = 180,000 + 21,450 = 201,450.00.
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hraEarningId, CalculationType.FLAT, new BigDecimal("5000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("201450.0000"),
                LocalDate.of(2026, 1, 1),
                "Initial Salary",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse response = salaryService.create(employeeId, req);

        assertThat(response.id()).isNotNull();
        assertThat(response.statutory()).hasSize(7);

        // Check rows in DB
        assertThat(countDbRows("payroll.ctc_epf_component", response.id())).isEqualTo(5);
        assertThat(countDbRows("payroll.ctc_esi_component", response.id())).isEqualTo(2);

        // Check employee and employer shares in response
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYEE");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("1200.0000"));
            assertThat(line.includedInCtc()).isFalse();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("833.0000"));
            assertThat(line.includedInCtc()).isTrue();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("367.0000"));
            assertThat(line.includedInCtc()).isTrue();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("ESI_EMPLOYEE");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("112.5000"));
            assertThat(line.includedInCtc()).isFalse();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("ESI_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("487.5000"));
            assertThat(line.includedInCtc()).isTrue();
        });
    }

    @Test
    @DisplayName("Revise version: new version has rows from rates in force, old version rows unchanged")
    void reviseVersion_derivesAfreshFromRatesInForce_oldVersionUnchanged() throws SQLException {
        TenantContext.set(TENANT_A);

        // 1. Create v1 effective 2026-01-01
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hraEarningId, CalculationType.FLAT, new BigDecimal("5000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest reqV1 = new SalaryVersionRequest(
                new BigDecimal("201450.0000"),
                LocalDate.of(2026, 1, 1),
                "V1 Offer",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse v1 = salaryService.create(employeeId, reqV1);
        BigDecimal v1EdliMonthly = v1.statutory().stream()
                .filter(l -> "EDLI".equals(l.componentCode()))
                .findFirst()
                .orElseThrow()
                .monthlyAmount();
        assertThat(v1EdliMonthly).isEqualByComparingTo(new BigDecimal("50.0000"));

        // 2. Change tenant EPF EDLI rate from 0.5% to 0.4% in force before revision
        updateEpfEdliRate(TENANT_A, new BigDecimal("0.4000"));

        // EDLI is now 10,000 * 0.4% = 40.0000 / mo (was 50). Annual difference = -120.
        // New employer statutory: EPS 833, EPF 367, EDLI 40, Admin 50, ESI 487.50 -> 1,777.50 * 12 = 21,330.
        // New Annual CTC = 180,000 + 21,330 = 201,330.0000.
        SalaryVersionRequest reqV2 = new SalaryVersionRequest(
                new BigDecimal("201330.0000"),
                LocalDate.of(2026, 4, 1),
                "V2 Revision",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse v2 = salaryService.revise(employeeId, reqV2);
        assertThat(v2.id()).isNotEqualTo(v1.id());
        assertThat(v2.statutory()).hasSize(7);

        // v2 derived afresh with new EDLI rate
        BigDecimal v2EdliMonthly = v2.statutory().stream()
                .filter(l -> "EDLI".equals(l.componentCode()))
                .findFirst()
                .orElseThrow()
                .monthlyAmount();
        assertThat(v2EdliMonthly).isEqualByComparingTo(new BigDecimal("40.0000"));

        // v1 in DB is completely unchanged
        BigDecimal v1EdliInDb = getDbMonthlyAmount("payroll.ctc_epf_component", v1.id(), "EDLI");
        assertThat(v1EdliInDb).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    @DisplayName("Update future version: rows rewritten, count unchanged")
    void updateFutureVersion_rewritesRows_countUnchanged() throws SQLException {
        TenantContext.set(TENANT_A);
        LocalDate futureDate = LocalDate.now().plusDays(30);

        // 1. Create future version
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hraEarningId, CalculationType.FLAT, new BigDecimal("5000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest createReq = new SalaryVersionRequest(
                new BigDecimal("201450.0000"),
                futureDate,
                "Future Initial",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse created = salaryService.create(employeeId, createReq);
        assertThat(created.statutory()).hasSize(7);
        assertThat(countDbRows("payroll.ctc_epf_component", created.id())).isEqualTo(5);
        assertThat(countDbRows("payroll.ctc_esi_component", created.id())).isEqualTo(2);

        // 2. Update future version: Increase Basic to 12,000 (HRA 5,000 -> Gross 17,000)
        // EPS: 12,000 * 8.33% = 999.6000
        // EPF: 12,000 * 3.67% = 440.4000
        // EDLI: 12,000 * 0.5% = 60.0000
        // Admin: 12,000 * 0.5% = 60.0000
        // ESI: 17,000 * 3.25% = 552.5000
        // Total Employer Statutory Monthly: 999.60 + 440.40 + 60 + 60 + 552.50 = 2,112.5000 * 12 = 25,350.
        // Gross annual: 17,000 * 12 = 204,000.
        // Annual CTC = 204,000 + 25,350 = 229,350.0000.
        SalaryComponentItemRequest newBasic = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("12000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest updateReq = new SalaryVersionRequest(
                new BigDecimal("229350.0000"),
                futureDate,
                "Future Updated",
                List.of(newBasic, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse updated = salaryService.update(employeeId, created.id(), updateReq);

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.statutory()).hasSize(7);
        assertThat(countDbRows("payroll.ctc_epf_component", created.id())).isEqualTo(5);
        assertThat(countDbRows("payroll.ctc_esi_component", created.id())).isEqualTo(2);

        // Verify updated amounts
        assertThat(updated.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("999.6000"));
        });
        assertThat(updated.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("440.4000"));
        });
        assertThat(updated.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("ESI_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("552.5000"));
        });
    }

    @Test
    @DisplayName("Salary save succeeds and derives statutory lines when employee has no personal row (W-31.3)")
    void salarySaveWithoutPersonalRow_succeedsAndDerivesStatutoryLines() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID empNoPersonal = UUID.randomUUID();
        seedEmployee(TENANT_A, empNoPersonal, "EMP-NO-PERSONAL", "Bob", "Jones");
        // No personal row seeded!
        seedStatutoryProfile(TENANT_A, empNoPersonal, true, true, true);

        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hraEarningId, CalculationType.FLAT, new BigDecimal("5000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("201450.0000"),
                LocalDate.of(2026, 1, 1),
                "Initial Salary without Personal",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse response = salaryService.create(empNoPersonal, req);
        assertThat(response).isNotNull();
        assertThat(response.statutory()).hasSize(7);
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("833.0000"));
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("367.0000"));
        });
    }

    @Test
    @DisplayName(
            "Salary save succeeds and derives statutory lines when employee has null date_of_birth in personal row (W-31.3)")
    void salarySaveWithNullDateOfBirthInPersonalRow_succeedsAndDerivesStatutoryLines() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID empNullDob = UUID.randomUUID();
        seedEmployee(TENANT_A, empNullDob, "EMP-NULL-DOB", "Charlie", "Brown");
        seedEmployeePersonal(TENANT_A, empNullDob, null);
        seedStatutoryProfile(TENANT_A, empNullDob, true, true, true);

        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hraEarningId, CalculationType.FLAT, new BigDecimal("5000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("201450.0000"),
                LocalDate.of(2026, 1, 1),
                "Initial Salary with Null DOB",
                List.of(basicItem, hraItem),
                List.of(),
                List.of());

        SalaryVersionResponse response = salaryService.create(empNullDob, req);
        assertThat(response).isNotNull();
        assertThat(response.statutory()).hasSize(7);
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("833.0000"));
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.monthlyAmount()).isEqualByComparingTo(new BigDecimal("367.0000"));
        });
    }

    private static int countDbRows(String table, UUID ctcStructureId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM " + table + " WHERE ctc_structure_id = ?")) {
            ps.setObject(1, ctcStructureId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static BigDecimal getDbMonthlyAmount(String table, UUID ctcStructureId, String code) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT monthly_amount FROM " + table + " WHERE ctc_structure_id = ? AND component_code = ?")) {
            ps.setObject(1, ctcStructureId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static void updateEpfEdliRate(UUID tenantId, BigDecimal newRate) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE payroll.epf_setting SET edli_rate = ? WHERE tenant_id = ?")) {
            ps.setBigDecimal(1, newRate);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, '2026-01-01', 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, code.toLowerCase() + "@example.com");
            ps.executeUpdate();
        }
    }

    private static void seedEmployeePersonal(UUID tenantId, UUID employeeId, LocalDate dob) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee_personal (id, tenant_id, employee_id, date_of_birth, marital_status, nationality) "
                                + "VALUES (gen_random_uuid(), ?, ?, ?, 'SINGLE', 'Indian') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, dob);
            ps.executeUpdate();
        }
    }

    private static void seedStatutoryProfile(
            UUID tenantId, UUID employeeId, boolean pfEligible, boolean epsEligible, boolean esiEligible)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.employee_statutory_profile (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi, contributes_eps_on_higher_wages) "
                                + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, false) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setBoolean(3, pfEligible);
            ps.setBoolean(4, epsEligible);
            ps.setBoolean(5, esiEligible);
            ps.executeUpdate();
        }
    }

    private static void seedEpfSetting(UUID tenantId, BigDecimal employeeRate, BigDecimal edliRate)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.epf_setting (id, tenant_id, is_enabled, wage_ceiling, restrict_employee_to_ceiling, restrict_employer_to_ceiling, employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, eps_senior_age, include_employer_in_ctc, include_edli_in_ctc, include_admin_in_ctc) "
                                + "VALUES (gen_random_uuid(), ?, true, 15000.0000, true, true, ?, 12.0000, 8.3300, ?, 0.5000, 58, true, true, true) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setBigDecimal(2, employeeRate);
            ps.setBigDecimal(3, edliRate);
            ps.executeUpdate();
        }
    }

    private static void seedEsiSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.esi_setting (id, tenant_id, is_enabled, wage_ceiling, employee_rate, employer_rate, include_employer_in_ctc) "
                                + "VALUES (gen_random_uuid(), ?, true, 21000.0000, 0.7500, 3.2500, true) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }
}
