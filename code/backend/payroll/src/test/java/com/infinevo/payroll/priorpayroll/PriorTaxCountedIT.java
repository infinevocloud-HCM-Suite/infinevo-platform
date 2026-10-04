package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.form16.Form16Service;
import com.infinevo.payroll.form16.Form16Statement;
import com.infinevo.payroll.payrun.EmployeePayRunLineResponse;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorRequest;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTdsController;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.payroll.tds.TdsFigures;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Acceptance test: imported TDS counts as tax already deducted (W-38.2 §7).
 *
 * <p>Imports 2026-04..2026-09 at 10,000 TDS each through W-38.1, records annual tax 120,000, then:
 * {@code year_to_date} reads 60,000 before any run; the 2026-10 run's TAX line is 10,000.0000
 * (60,000 / 6 months); Form 16 shows the imports in Q1 and Q2. Tenant B's imports for an employee
 * with the same number are never counted for tenant A. Run setup as {@code PayRunTaxLineIT} (W-36.1).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class PriorTaxCountedIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Autowired
    private PriorPayrollImportService importService;

    @Autowired
    private PriorPayrollTaxQuery priorTax;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private EmployeeTdsService tdsService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TaxDeductorService taxDeductorService;

    @Autowired
    private Form16Service form16Service;

    private UUID employeeA;
    private UUID employeeB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
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

        // Tenant B: an employee with the same number, imported with a much larger TDS
        employeeB = PayRunTestSchema.insertPayableEmployee(TENANT_B, "W-01");
        TenantContext.set(TENANT_B);
        importSixMonths(TENANT_B, "50000", "48000");

        // Tenant A: the worked-example employee from PayRunTaxLineIT
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        employeeA = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        importSixMonths(TENANT_A, "10000", "88000");

        tdsService.record(
                employeeA,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        null,
                        "Annual tax"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "imported 60,000 ⇒ year_to_date 60,000 before the run, October TAX line 10,000.0000, tenant B never counted")
    void importedTaxCountsAsAlreadyDeducted() {
        // GET …/tds/2026-2027 before any run
        EmployeeTdsController controller = new EmployeeTdsController(tdsService, employeeService);
        assertThat(controller.get(employeeA, FY).data().yearToDate()).isEqualByComparingTo("60000.0000");

        // The query, bound to tenant A, sees A's imports only; B's rows are hidden by tenant and RLS
        FinancialYear fy = FinancialYear.parse(FY);
        assertThat(priorTax.total(TENANT_A, employeeA, fy)).isEqualByComparingTo("60000.0000");
        assertThat(priorTax.byPeriod(TENANT_A, employeeA, fy)).hasSize(6);
        assertThat(priorTax.total(TENANT_B, employeeB, fy)).isEqualByComparingTo("0");
        assertThat(priorTax.total(TENANT_A, employeeB, fy)).isEqualByComparingTo("0");

        // October run: (120,000 − 60,000) / 6 months
        PayRunResponse october = payRunService.create(OCTOBER);
        payRunService.lock(october.id());
        assertThat(worker.computeNow(october.id()).status()).isEqualTo(PayRunStatus.COMPUTED);

        List<EmployeePayRunLineResponse> taxLines = payRunService.lines(october.id(), employeeA).lines().stream()
                .filter(l -> l.source() == LineSource.TAX)
                .toList();
        assertThat(taxLines).hasSize(1);
        assertThat(taxLines.get(0).componentCode()).isEqualTo("TDS");
        assertThat(taxLines.get(0).amount()).isEqualByComparingTo("10000.0000");

        // After the run, year to date counts the imports and the October line together
        assertThat(controller.get(employeeA, FY).data().yearToDate()).isEqualByComparingTo("70000.0000");

        // Form 16: imports fill Q1 and Q2; October is COMPUTED, not PAID, so Q3 is 0 and the year is not final
        taxDeductorService.save(new TaxDeductorRequest(
                "MUMA12345B", "AAACM1234F", "MUM/TD/001/01", null, "Rajesh Kumar", "Suresh Kumar", "Director"));
        Form16Statement statement = form16Service.render(employeeA, FY);
        assertThat(statement.quarters().get(0).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(1).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(2).amountDeducted()).isEqualByComparingTo("0.00");
        assertThat(statement.quarters().get(3).amountDeducted()).isEqualByComparingTo("0.00");
        assertThat(statement.totalDeducted()).isEqualByComparingTo("60000.00");
        assertThat(statement.balance()).isEqualByComparingTo("60000.00");
        assertThat(statement.isFinal()).isFalse();
    }

    private void importSixMonths(UUID tenantId, String tds, String net) throws SQLException {
        StringBuilder csv = new StringBuilder(
                "employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay\n");
        for (int m = 4; m <= 9; m++) {
            csv.append(String.format("W-01,2026-%02d,100000,1800,0,200,%s,%s%n", m, tds, net));
        }
        UUID docId = PayrollTestSchema.insertDocument(
                tenantId, "prior_" + tenantId + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8));
        PriorPayrollImportResponse response = importService.importFile(docId, FY, false);
        assertThat(response.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED);
        assertThat(response.rowsImported()).isEqualTo(6);
    }
}
