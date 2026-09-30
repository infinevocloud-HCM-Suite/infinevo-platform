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
 * W-29.3 §7 — a tenant whose loss-of-pay policy row is gone: every included row carries a
 * {@code computation_error} naming {@code NoLopPolicyException}, the run is FAILED, and no line is
 * written for them. The tenant is never paid on a guessed divisor (W-18.1 §13 decision 1).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunNoPolicyIT extends AbstractIntegrationTest {

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
    @DisplayName("No policy: every row fails naming NoLopPolicyException, the run is FAILED, no lines")
    void noPolicyFailsEveryEmployee() throws SQLException {
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        UUID first = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "N-01", catalogue);
        UUID second = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "N-02", catalogue);
        PayRunResponse run = payRunService.create(YearMonth.of(2026, 7));
        payRunService.lock(run.id());
        PayRunTestSchema.deletePolicy(TENANT_A);

        PayRunResponse computed = payRunService.compute(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(computed.failureReason()).isEqualTo("2 of 2 employees could not be computed");
        assertThat(computed.totalNetPay()).isEqualByComparingTo("0");
        assertThat(payRunService
                        .employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent())
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.computationError()).startsWith("NoLopPolicyException"));
        for (UUID employee : List.of(first, second)) {
            assertThat(payRunService.lines(run.id(), employee).lines()).isEmpty();
        }
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isZero();
    }
}
