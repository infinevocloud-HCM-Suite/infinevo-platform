package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.money.Money;
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
 * W-29.3 §7 — an ad-hoc deduction larger than the salary: {@code net_pay} is negative, stored as
 * such — legacy floored it to zero ({@code :1178-1180}) — and the run counts it in
 * {@code negative_net_count}. A negative net is not a failure: the run is COMPUTED.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunNegativeNetIT extends AbstractIntegrationTest {

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

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
    @DisplayName("A 60,000 ad-hoc deduction against 47,000: net −13,000.00, stored negative, counted once")
    void negativeNetIsKeptAndCounted() throws SQLException {
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        UUID indebted = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "G-01", catalogue);
        PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "G-02", catalogue);
        YearMonth july = YearMonth.of(2026, 7);
        payInputService.record(new PayInputCommand(
                indebted,
                july,
                PayInputKind.AD_HOC_DEDUCTION,
                BigDecimal.ONE,
                Money.of("60000"),
                "payroll",
                "advance-" + UUID.randomUUID()));
        PayRunResponse run = payRunService.create(july);
        payRunService.lock(run.id());

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.negativeNetCount()).isEqualTo(1);
        assertThat(computed.totalNetPay()).isEqualByComparingTo("34000.00");
        assertThat(payRunService
                        .employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent())
                .filteredOn(r -> r.employeeId().equals(indebted))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.netPay()).isEqualByComparingTo("-13000.00");
                    assertThat(r.totalDeductions()).isEqualByComparingTo("60000.0000");
                    assertThat(r.computationError()).isNull();
                });
    }
}
