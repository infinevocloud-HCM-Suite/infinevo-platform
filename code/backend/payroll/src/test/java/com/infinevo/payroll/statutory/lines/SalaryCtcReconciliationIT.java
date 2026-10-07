package com.infinevo.payroll.statutory.lines;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryValidationException;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * Acceptance integration test for CTC reconciliation with statutory employer lines (W-31.3).
 * Spec §7: with include_employer_in_ctc=true, a version whose earnings alone equal annual_ctc
 * is refused with the employer PF in the difference message; with it false, accepted.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class SalaryCtcReconciliationIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    private UUID employeeId;
    private UUID basicEarningId;

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
        seedStatutoryProfile(TENANT_A, employeeId);

        TenantContext.set(TENANT_A);
        Earning basic = new Earning(TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic Salary");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic = earningRepository.save(basic);
        basicEarningId = basic.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "With include_employer_in_ctc=true, earnings alone equal to annual_ctc is refused with employer PF difference")
    void includeEmployerInCtcTrue_refusedWithEmployerDifference() throws SQLException {
        // Employer PF is included in CTC (12% of 10,000 = 1,200/mo = 14,400/yr). EDLI & Admin not in CTC.
        seedEpfSetting(TENANT_A, true, false, false);

        TenantContext.set(TENANT_A);
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);

        // Earnings alone = 120,000. Request annualCtc = 120,000.
        // But employer PF = 14,400. Total included = 134,400. Difference = 14400.00.
        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("120000.0000"),
                LocalDate.of(2026, 1, 1),
                "Offer",
                List.of(basicItem),
                List.of(),
                List.of());

        assertThatThrownBy(() -> salaryService.create(employeeId, req))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining(
                        "The annual sum of components included in CTC (134400.00) does not equal annual CTC (120000.00). Difference: 14400.00");
    }

    @Test
    @DisplayName("With include_employer_in_ctc=false, earnings alone equal to annual_ctc is accepted")
    void includeEmployerInCtcFalse_accepted() throws SQLException {
        // Employer PF not in CTC
        seedEpfSetting(TENANT_A, false, false, false);

        TenantContext.set(TENANT_A);
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("120000.0000"),
                LocalDate.of(2026, 1, 1),
                "Offer",
                List.of(basicItem),
                List.of(),
                List.of());

        SalaryVersionResponse response = salaryService.create(employeeId, req);
        assertThat(response.id()).isNotNull();
        assertThat(response.annualCtc()).isEqualByComparingTo(new BigDecimal("120000.0000"));

        // Statutory lines exist, but employer lines have includedInCtc = false
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.includedInCtc()).isFalse();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.includedInCtc()).isFalse();
        });
    }

    @Test
    @DisplayName("D-39: EDLI in CTC alone adds only EDLI (600 a year) to the CTC sum")
    void edliInCtcAlone_refusedWithEdliDifference() throws SQLException {
        // Basic 10,000: EDLI 0.5% = 50/mo = 600/yr; admin 0.5% = 600/yr but not in CTC.
        seedEpfSetting(TENANT_A, false, true, false);

        TenantContext.set(TENANT_A);
        SalaryVersionRequest req = basicOnlyRequest(new BigDecimal("120000.0000"));

        assertThatThrownBy(() -> salaryService.create(employeeId, req))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining(
                        "The annual sum of components included in CTC (120600.00) does not equal annual CTC (120000.00). Difference: 600.00");
    }

    @Test
    @DisplayName("D-39: EDLI in CTC and admin out of CTC is accepted at earnings plus EDLI, flags set per line")
    void edliInCtc_adminOut_accepted() throws SQLException {
        seedEpfSetting(TENANT_A, false, true, false);

        TenantContext.set(TENANT_A);
        SalaryVersionResponse response =
                salaryService.create(employeeId, basicOnlyRequest(new BigDecimal("120600.0000")));

        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EDLI");
            assertThat(line.includedInCtc()).isTrue();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_ADMIN");
            assertThat(line.includedInCtc()).isFalse();
        });
    }

    @Test
    @DisplayName("D-39: admin in CTC and EDLI out of CTC is accepted at earnings plus admin, flags set per line")
    void adminInCtc_edliOut_accepted() throws SQLException {
        seedEpfSetting(TENANT_A, false, false, true);

        TenantContext.set(TENANT_A);
        SalaryVersionResponse response =
                salaryService.create(employeeId, basicOnlyRequest(new BigDecimal("120600.0000")));

        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EDLI");
            assertThat(line.includedInCtc()).isFalse();
        });
        assertThat(response.statutory()).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_ADMIN");
            assertThat(line.includedInCtc()).isTrue();
        });
    }

    private SalaryVersionRequest basicOnlyRequest(BigDecimal annualCtc) {
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("10000.0000"), null, true, "MONTHLY", null);
        return new SalaryVersionRequest(
                annualCtc, LocalDate.of(2026, 1, 1), "Offer", List.of(basicItem), List.of(), List.of());
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

    private static void seedStatutoryProfile(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.employee_statutory_profile (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi, contributes_eps_on_higher_wages) "
                                + "VALUES (gen_random_uuid(), ?, ?, true, true, false, false) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }
    }

    private static void seedEpfSetting(
            UUID tenantId, boolean includeEmployerInCtc, boolean includeEdliInCtc, boolean includeAdminInCtc)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.epf_setting (id, tenant_id, is_enabled, wage_ceiling, restrict_employee_to_ceiling, restrict_employer_to_ceiling, employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, eps_senior_age, include_employer_in_ctc, include_edli_in_ctc, include_admin_in_ctc) "
                                + "VALUES (gen_random_uuid(), ?, true, 15000.0000, true, true, 12.0000, 12.0000, 8.3300, 0.5000, 0.5000, 58, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setBoolean(2, includeEmployerInCtc);
            ps.setBoolean(3, includeEdliInCtc);
            ps.setBoolean(4, includeAdminInCtc);
            ps.executeUpdate();
        }
    }
}
