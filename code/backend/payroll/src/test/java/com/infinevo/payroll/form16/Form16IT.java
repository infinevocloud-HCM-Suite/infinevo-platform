package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxcalc.recalc.TaxTrigger;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeductor.TaxDeductorRequest;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.payroll.tds.TdsFigures;
import com.infinevo.payroll.tds.TdsSource;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
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
 * Integration tests for Form 16 Part B annual tax statement (W-36.4).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class Form16IT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private Form16Service form16Service;

    @Autowired
    private TaxDeductorService taxDeductorService;

    @Autowired
    private EmployeeTdsService employeeTdsService;

    @Autowired
    private TaxComputationRepository taxComputationRepository;

    @Autowired
    private EmployeeService employeeService;

    private Form16Controller controller;

    private UUID employeeIdA;
    private UUID employeeIdB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayRunTestSchema.clean();
        PayrollTestSchema.seedTenants();

        employeeIdA =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-F16-01", "rajesh@acme.com", "Rajesh", "Sharma");
        employeeIdB =
                TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-F16-02", "vikram@globex.com", "Vikram", "Mehta");

        TenantContext.set(TENANT_A);
        controller = new Form16Controller(form16Service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @Test
    @DisplayName("Throws DeductorNotSetException (HTTP 409) when deductor is not configured for tenant")
    void missingDeductorThrowsDeductorNotSetException() {
        employeeTdsService.record(
                employeeIdA,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        null,
                        "Initial tax record",
                        TdsSource.OFFICER));

        assertThatThrownBy(() -> form16Service.render(employeeIdA, FY)).isInstanceOf(DeductorNotSetException.class);
    }

    @Test
    @DisplayName("Throws EmployeeTdsNotFoundException (HTTP 404) when no active TDS record exists")
    void missingTdsRecordThrowsNotFound() {
        taxDeductorService.save(new TaxDeductorRequest(
                "MUMA12345B", "AAACM1234F", "MUM/TD/001/01", null, "Rajesh Kumar", "Suresh Kumar", "Director"));

        assertThatThrownBy(() -> form16Service.render(employeeIdA, FY))
                .isInstanceOf(EmployeeTdsNotFoundException.class);
    }

    @Test
    @DisplayName("Renders complete Form 16 Part B statement with tax computation and quarter tax lines")
    void rendersCompleteForm16Statement() throws Exception {
        // 1. Configure deductor
        taxDeductorService.save(new TaxDeductorRequest(
                "MUMA12345B", "AAACM1234F", "MUM/TD/001/01", null, "Rajesh Kumar", "Suresh Kumar", "Director"));

        // 2. Seed active TDS
        employeeTdsService.record(
                employeeIdA,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1500000.00"),
                        new BigDecimal("1425000.00"),
                        new BigDecimal("156000.00"),
                        "2026-04",
                        null,
                        "Annual tax computation",
                        TdsSource.OFFICER));

        // 3. Record tax computation breakdown
        TaxComputationRecord compRecord = new TaxComputationRecord(
                TENANT_A,
                employeeIdA,
                null,
                FY,
                "NEW",
                TaxTrigger.OFFICER,
                new BigDecimal("1500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("75000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1425000.00"),
                new BigDecimal("150000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("6000.00"),
                BigDecimal.ZERO,
                new BigDecimal("156000.00"),
                "{}",
                Instant.now(),
                null,
                "test");
        taxComputationRepository.save(compRecord);

        // 4. Seed paid pay run and employee tax lines in Q1 (2026-04 and 2026-05)
        seedPaidTaxLine(TENANT_A, employeeIdA, "2026-04", new BigDecimal("13000.00"));
        seedPaidTaxLine(TENANT_A, employeeIdA, "2026-05", new BigDecimal("13000.00"));

        // 5. Render statement
        Form16Statement statement = form16Service.render(employeeIdA, FY);

        assertThat(statement).isNotNull();
        assertThat(statement.financialYear()).isEqualTo(FY);
        assertThat(statement.assessmentYear()).isEqualTo("2027-2028");
        assertThat(statement.regime()).isEqualTo("NEW");
        assertThat(statement.isFinal()).isFalse(); // only 2 paid periods, less than 12 -> provisional

        // Deductor assertions
        assertThat(statement.deductor()).isNotNull();
        assertThat(statement.deductor().tan()).isEqualTo("MUMA12345B");
        assertThat(statement.deductor().pan()).isEqualTo("AAACM1234F");
        assertThat(statement.deductor().signatoryName()).isEqualTo("Rajesh Kumar");

        // Tax breakdown assertions
        assertThat(statement.breakdown()).isNotNull();
        assertThat(statement.breakdown().grossTotalIncome()).isEqualByComparingTo("1500000.00");
        assertThat(statement.breakdown().standardDeduction()).isEqualByComparingTo("75000.00");
        assertThat(statement.breakdown().taxableIncome()).isEqualByComparingTo("1425000.00");
        assertThat(statement.breakdown().annualTax()).isEqualByComparingTo("156000.00");

        // Quarters assertions
        assertThat(statement.quarters()).hasSize(4);
        QuarterTax q1 = statement.quarters().get(0);
        assertThat(q1.quarter()).isEqualTo("Q1");
        assertThat(q1.amountDeducted()).isEqualByComparingTo("26000.00");
        assertThat(statement.totalDeducted()).isEqualByComparingTo("26000.00");

        // Verify controller returns 200 OK
        Form16Response resp = controller.get(employeeIdA, FY);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.data().financialYear()).isEqualTo(FY);
    }

    @Test
    @DisplayName("Self-service endpoint getOwn returns Form 16 statement for authenticated employee")
    void selfServiceGetOwnReturnsStatement() {
        taxDeductorService.save(new TaxDeductorRequest(
                "MUMA12345B", "AAACM1234F", "MUM/TD/001/01", null, "Rajesh Kumar", "Suresh Kumar", "Director"));

        employeeTdsService.record(
                employeeIdA,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1125000.00"),
                        new BigDecimal("90000.00"),
                        "2026-04",
                        null,
                        "Self service tax record",
                        TdsSource.OFFICER));

        // Set current employee
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeIdA));

        Form16Response resp = controller.getOwn(FY);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.data()).isNotNull();
        assertThat(resp.data().financialYear()).isEqualTo(FY);
        assertThat(resp.data().employee().name()).contains("Rajesh Sharma");
    }

    private void seedPaidTaxLine(UUID tenantId, UUID employeeId, String period, BigDecimal taxAmount) throws Exception {
        UUID payRunId = UUID.randomUUID();
        UUID empPayRunId = UUID.randomUUID();

        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            // Seed paid payrun
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.payrun (
                        id, tenant_id, period, period_start, period_end, cutoff_date, pay_date, paid_on,
                        run_type, status, included_count, skipped_count, created_by, updated_by
                    ) VALUES (
                        ?, ?, ?, DATE '2026-04-01', DATE '2026-04-30', DATE '2026-04-25', DATE '2026-04-30', DATE '2026-04-30',
                        'REGULAR', 'PAID', 1, 0, 'seed', 'seed'
                    )
                    """)) {
                ps.setObject(1, payRunId);
                ps.setObject(2, tenantId);
                ps.setString(3, period);
                ps.executeUpdate();
            }

            // Seed employee payrun
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_payrun (
                        id, tenant_id, payrun_id, employee_id, inclusion_status, created_by, updated_by
                    ) VALUES (
                        ?, ?, ?, ?, 'INCLUDED', 'seed', 'seed'
                    )
                    """)) {
                ps.setObject(1, empPayRunId);
                ps.setObject(2, tenantId);
                ps.setObject(3, payRunId);
                ps.setObject(4, employeeId);
                ps.executeUpdate();
            }

            // Seed tax deduction line
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_payrun_line (
                        id, tenant_id, employee_payrun_id, payrun_id, line_kind, source,
                        component_code, component_name, amount, is_taxable, sort_order, created_by, updated_by
                    ) VALUES (
                        ?, ?, ?, ?, 'DEDUCTION', 'TAX',
                        'TAX_INCOME_TAX', 'Income Tax TDS', ?, false, 1, 'seed', 'seed'
                    )
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, empPayRunId);
                ps.setObject(4, payRunId);
                ps.setBigDecimal(5, taxAmount);
                ps.executeUpdate();
            }
        }
    }
}
