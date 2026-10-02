package com.infinevo.payroll.tds;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.EmployeePayRunLineResponse;
import com.infinevo.payroll.payrun.EmployeePayRunLinesResponse;
import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
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
import org.springframework.data.domain.PageRequest;

/**
 * Acceptance test for pay run tax lines (W-36.1 §7).
 *
 * <p>Flow:
 * <ol>
 *   <li>Record annual tax 120,000 from 2026-04</li>
 *   <li>Lock and compute the April run ⇒ one TAX DEDUCTION line of 10,000.0000 and total_deductions includes it</li>
 *   <li>Compute May ⇒ 10,000.0000 again (ytd 10,000, 11 months remaining)</li>
 *   <li>Recompute April ⇒ still 10,000.0000 (its own line was not counted)</li>
 *   <li>Approve and cancel May, then compute June ⇒ ytd counts April only (remaining 110,000 / 10 months = 11,000.0000)</li>
 *   <li>PayLineContributor beans include TaxLineContributor (count >= 4)</li>
 * </ol>
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class PayRunTaxLineIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private EmployeeTdsService tdsService;

    @Autowired
    private List<PayLineContributor> contributors;

    private UUID employeeId;

    private PayRunTestSchema.Catalogue catalogue;

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

        catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        employeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);

        // Record annual tax of 120,000 starting from 2026-04
        tdsService.record(
                employeeId,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        null,
                        "Initial tax record"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Pay run tax line acceptance: April 10,000, May 10,000, April recompute safe, May cancelled ⇒ June 11,000")
    void fullTaxLineCycle() {
        // Step 1: April run computation
        PayRunResponse april = payRunService.create(APRIL);
        payRunService.lock(april.id());
        PayRunResponse aprilComputed = worker.computeNow(april.id());

        assertThat(aprilComputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse aprilLines = payRunService.lines(april.id(), employeeId);
        EmployeePayRunLineResponse aprilTaxLine = aprilLines.lines().stream()
                .filter(l -> l.source() == LineSource.TAX && "TDS".equals(l.componentCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected TDS line in April run"));

        assertThat(aprilTaxLine.lineKind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(aprilTaxLine.amount()).isEqualByComparingTo("10000.0000");

        EmployeePayRunResponse aprilEmpRow =
                payRunService
                        .employees(april.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent()
                        .stream()
                        .filter(r -> r.employeeId().equals(employeeId))
                        .findFirst()
                        .orElseThrow();
        assertThat(aprilEmpRow.totalDeductions()).isEqualByComparingTo("10000.0000");
        assertThat(aprilEmpRow.computationNote()).isNull();

        // Step 2: May run computation (YTD = 10,000, remaining = 110,000 over 11 months = 10,000.0000)
        PayRunResponse may = payRunService.create(MAY);
        payRunService.lock(may.id());
        PayRunResponse mayComputed = worker.computeNow(may.id());

        assertThat(mayComputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse mayLines = payRunService.lines(may.id(), employeeId);
        EmployeePayRunLineResponse mayTaxLine = mayLines.lines().stream()
                .filter(l -> l.source() == LineSource.TAX && "TDS".equals(l.componentCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected TDS line in May run"));
        assertThat(mayTaxLine.amount()).isEqualByComparingTo("10000.0000");

        // Step 3: Recompute April run (its own lines are excluded so remaining = 120,000 / 12 = 10,000.0000)
        PayRunResponse aprilRecomputed = worker.computeNow(april.id());
        assertThat(aprilRecomputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse aprilRecomputedLines = payRunService.lines(april.id(), employeeId);
        EmployeePayRunLineResponse aprilRecomputedTaxLine = aprilRecomputedLines.lines().stream()
                .filter(l -> l.source() == LineSource.TAX && "TDS".equals(l.componentCode()))
                .findFirst()
                .orElseThrow();
        assertThat(aprilRecomputedTaxLine.amount()).isEqualByComparingTo("10000.0000");

        // Step 4: Cancel May run (first approve, then cancel)
        payRunService.approve(may.id());
        payRunService.cancel(may.id());

        // Step 5: Compute June run
        // YTD counts April (10,000) only, because May was CANCELLED.
        // Remaining = 120,000 - 10,000 = 110,000.
        // Months June through March = 10 months.
        // 110,000 / 10 = 11,000.0000.
        PayRunResponse june = payRunService.create(JUNE);
        payRunService.lock(june.id());
        PayRunResponse juneComputed = worker.computeNow(june.id());

        assertThat(juneComputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse juneLines = payRunService.lines(june.id(), employeeId);
        EmployeePayRunLineResponse juneTaxLine = juneLines.lines().stream()
                .filter(l -> l.source() == LineSource.TAX && "TDS".equals(l.componentCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected TDS line in June run"));
        assertThat(juneTaxLine.amount()).isEqualByComparingTo("11000.0000");

        // Step 6: Contributor bean count check
        assertThat(contributors).hasSizeGreaterThanOrEqualTo(4);
        assertThat(contributors.stream().anyMatch(c -> c instanceof TaxLineContributor))
                .isTrue();
    }

    @Test
    @DisplayName("An employee with no TDS record gets no tax line, and the run's row says so")
    void noRecordIsNotedOnTheRow() throws Exception {
        UUID noRecord = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-02", catalogue);

        PayRunResponse april = payRunService.create(APRIL);
        payRunService.lock(april.id());
        assertThat(worker.computeNow(april.id()).status()).isEqualTo(PayRunStatus.COMPUTED);

        assertThat(payRunService.lines(april.id(), noRecord).lines()).noneMatch(l -> l.source() == LineSource.TAX);
        EmployeePayRunResponse row =
                payRunService
                        .employees(april.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent()
                        .stream()
                        .filter(r -> r.employeeId().equals(noRecord))
                        .findFirst()
                        .orElseThrow();
        assertThat(row.computationNote()).isEqualTo("No TDS record for " + FY);
    }
}
