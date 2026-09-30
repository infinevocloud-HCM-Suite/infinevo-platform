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
import org.springframework.data.domain.PageRequest;

/**
 * W-29.2 §7 — one of three employees has a version whose component was deleted after inclusion: the
 * other two compute, that row carries {@code computation_error}, the run is FAILED with the count;
 * restoring the component and recomputing reaches COMPUTED.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunComputeFailureIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A deleted component fails one employee, not the run's other two; a fix and a recompute succeed")
    void oneBadEmployeeDoesNotFailTheOthers() throws SQLException {
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        UUID good1 = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "F-01", catalogue);
        UUID good2 = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "F-02", catalogue);
        UUID bad = PayRunTestSchema.insertEmployee(TENANT_A, "F-03", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, bad);
        UUID badCtc = PayRunTestSchema.insertSalary(TENANT_A, bad, LocalDate.of(2025, 1, 1));
        UUID retired =
                PayRunTestSchema.insertEarningComponent(TENANT_A, "RETIRED", "Retired allowance", false, true, false);
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, badCtc, retired, "3000.0000", null);

        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        // Deleted after inclusion: the version still names it, the catalogue no longer holds it.
        PayRunTestSchema.execute("UPDATE payroll.earning SET is_deleted = true WHERE id = ?", retired);

        PayRunResponse failed = payRunService.compute(run.id());

        assertThat(failed.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("1 of 3 employees could not be computed");
        assertThat(failed.totalNetPay()).isEqualByComparingTo("94000.00");
        assertThat(payRunService.lines(run.id(), bad).computationError()).contains("no longer in the catalogue");
        assertThat(payRunService.lines(run.id(), bad).lines()).isEmpty();
        assertThat(payRunService.lines(run.id(), good1).lines()).hasSize(7);
        assertThat(payRunService.lines(run.id(), good2).computationError()).isNull();
        assertThat(payRunService
                        .employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent())
                .filteredOn(r -> r.employeeId().equals(bad))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.computationError()).isNotBlank();
                    assertThat(r.netPay()).isEqualByComparingTo("0");
                });

        PayRunTestSchema.execute("UPDATE payroll.earning SET is_deleted = false WHERE id = ?", retired);
        PayRunResponse fixed = payRunService.compute(run.id());

        assertThat(fixed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(fixed.failureReason()).isNull();
        assertThat(fixed.totalNetPay()).isEqualByComparingTo("97000.00");
        assertThat(payRunService.lines(run.id(), bad).computationError()).isNull();
        assertThat(payRunService.lines(run.id(), bad).lines()).hasSize(1);
    }
}
