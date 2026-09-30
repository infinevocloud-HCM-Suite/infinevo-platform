package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
 * W-29.2 §7 — lock, then compute: the run is COMPUTED, every included employee has lines, and the
 * row and run totals are the sums of those lines — the §8 worked example, end to end through the
 * real salary read. A second compute replaces the lines; a compute from DRAFT is refused.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunComputeIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID first;
    private UUID second;

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
        first = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        second = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-02", catalogue);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lock then compute: COMPUTED, the worked example's lines, row and run totals equal the sums")
    void computeWritesLinesAndTotals() throws SQLException {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.computedAt()).isNotNull();
        assertThat(computed.failureReason()).isNull();
        assertThat(computed.totalGross()).isEqualByComparingTo("90000.0000");
        assertThat(computed.totalDeductions()).isEqualByComparingTo("0");
        assertThat(computed.totalNetPay()).isEqualByComparingTo("94000.00");

        EmployeePayRunLinesResponse lines = payRunService.lines(run.id(), first);
        assertThat(lines.computationError()).isNull();
        assertThat(lines.lines())
                .extracting(
                        EmployeePayRunLineResponse::componentCode,
                        EmployeePayRunLineResponse::lineKind,
                        EmployeePayRunLineResponse::amount,
                        EmployeePayRunLineResponse::taxable)
                .containsExactlyInAnyOrder(
                        tuple("BASIC", LineKind.EARNING, new BigDecimal("25000.0000"), true),
                        tuple("HRA", LineKind.EARNING, new BigDecimal("10000.0000"), true),
                        tuple("SPECIAL", LineKind.EARNING, new BigDecimal("7500.0000"), true),
                        tuple("MEAL", LineKind.EARNING, new BigDecimal("1500.0000"), false),
                        tuple("MEAL", LineKind.EARNING, new BigDecimal("1000.0000"), true),
                        tuple("EMPLOYER_PF", LineKind.BENEFIT, new BigDecimal("1800.0000"), false),
                        tuple("FUEL", LineKind.REIMBURSEMENT, new BigDecimal("2000.0000"), false));
        assertThat(lines.lines())
                .extracting(EmployeePayRunLineResponse::sortOrder)
                .isSorted();

        EmployeePayRunResponse row =
                payRunService.employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10)).getContent().stream()
                        .filter(r -> r.employeeId().equals(first))
                        .findFirst()
                        .orElseThrow();
        assertThat(row.grossEarnings()).isEqualByComparingTo("45000.0000");
        assertThat(row.totalReimbursements()).isEqualByComparingTo("2000.0000");
        assertThat(row.totalBenefits()).isEqualByComparingTo("1800.0000");
        assertThat(row.totalDeductions()).isEqualByComparingTo("0");
        assertThat(row.netPay()).isEqualByComparingTo("47000.00");
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isEqualTo(14);
    }

    @Test
    @DisplayName("Computing again replaces the lines: the same count, no duplicates, the same totals")
    void recomputeReplacesLines() throws SQLException {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        worker.computeNow(run.id());
        long firstCount = PayRunTestSchema.countLines(TENANT_A, run.id());

        PayRunResponse again = worker.computeNow(run.id());

        assertThat(again.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id()))
                .isEqualTo(firstCount)
                .isEqualTo(14);
        assertThat(again.totalNetPay()).isEqualByComparingTo("94000.00");
        assertThat(payRunService.lines(run.id(), second).lines()).hasSize(7);
    }

    @Test
    @DisplayName("Compute from DRAFT is refused and writes nothing; so is compute of a CANCELLED run")
    void computeNeedsALockedRun() throws SQLException {
        PayRunResponse run = payRunService.create(JULY);

        assertThatThrownBy(() -> payRunService.compute(run.id())).isInstanceOf(IllegalPayRunTransitionException.class);
        assertThat(payRunService.get(run.id()).status()).isEqualTo(PayRunStatus.DRAFT);
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isZero();

        payRunService.cancel(run.id());
        assertThatThrownBy(() -> payRunService.compute(run.id())).isInstanceOf(IllegalPayRunTransitionException.class);
    }
}
