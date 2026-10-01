package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
 * W-18.2 §7 — a missing policy is never defaulted. A tenant with no policy: the run completes
 * {@code FAILED}, every employee failed with the reason, no line and no stamp written. W-29.1 creates
 * the rows when the run is created, so "no row" is "no figure on the row": the rows stay, empty, with
 * the reason. And on an {@code ORG_DAYS} tenant whose holidays are unpaid, one employee with no work
 * location — no holiday calendar to count by — fails alone while the rest are computed and stamped.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class NoPolicyFailsEmployeeIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    private UUID lastEmployee;

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
        for (int i = 1; i <= 3; i++) {
            lastEmployee = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "F-0" + i, catalogue);
        }
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.EMPLOYEES_WITHOUT_LOCATION.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("No policy: the run completes FAILED, every employee failed with the reason, no figure, no stamp")
    void noPolicyFailsEveryEmployeeAndStampsNothing() throws SQLException {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        PayRunTestSchema.deletePolicy(TENANT_A);

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(computed.failureReason()).isEqualTo("3 of 3 employees could not be computed");
        assertThat(computed.totalNetPay()).isEqualByComparingTo("0");
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isZero();
        assertThat(PayRunTestSchema.stamps(TENANT_A, run.id())).hasSize(3).allSatisfy(row -> {
            assertThat(row.computationError()).startsWith("NoLopPolicyException");
            assertThat(row.unstamped()).as("no stamp without a policy").isTrue();
        });
    }

    @Test
    @DisplayName("ORG_DAYS, holidays unpaid: the employee with no work location fails alone; the other two are stamped")
    void employeeWithoutWorkLocationFailsAlone() throws SQLException {
        PayRunTestSchema.setPolicy(TENANT_A, "ORG_DAYS", false, false, "HALF_UP_2");
        PayrollTestApp.EMPLOYEES_WITHOUT_LOCATION.add(lastEmployee);
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(computed.failureReason()).isEqualTo("1 of 3 employees could not be computed");
        List<PayRunTestSchema.StampRow> rows = PayRunTestSchema.stamps(TENANT_A, run.id());
        assertThat(rows)
                .filteredOn(row -> row.employeeId().equals(lastEmployee))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.computationError())
                            .startsWith("NoLopPolicyException")
                            .contains("has no work location");
                    assertThat(row.unstamped()).isTrue();
                });
        assertThat(rows)
                .filteredOn(row -> !row.employeeId().equals(lastEmployee))
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.computationError()).isNull();
                    assertThat(row.fullyStamped()).isTrue();
                    assertThat(row.workingDayBasis()).isEqualTo("ORG_DAYS");
                    // July 2026, Monday to Friday from the pay schedule, no holidays: 23.
                    assertThat(row.payDivisor()).isEqualByComparingTo("23.00");
                });
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isEqualTo(14);
    }
}
