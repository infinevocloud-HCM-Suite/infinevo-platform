package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasis;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
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

/**
 * W-18.2 §7 — the build order's acceptance criterion W-18.1 could only half-prove: two tenants, the
 * same employee, the same 1.5 LOP days in July, different policies — {@code ACTUAL_DAYS} (31) and
 * {@code ORG_DAYS} with 26 configured — give different loss-of-pay amounts, and each row's stamp,
 * read back through the explanation, accounts for its own figure: 42,500 × 1.5 ÷ divisor.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TwoTenantFigureIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final BigDecimal PRO_RATA_BASE = new BigDecimal("42500");
    private static final BigDecimal LOP_DAYS = new BigDecimal("1.5");

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayFigureExplanationService explanationService;

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
        PayRunTestSchema.setPolicy(TENANT_A, "ACTUAL_DAYS", true, true, "HALF_UP_2");
        PayRunTestSchema.setPolicy(TENANT_B, "ORG_DAYS", new BigDecimal("26"), true, true, "HALF_UP_2");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ACTUAL_DAYS 2,056.45 and ORG_DAYS(26) 2,451.92 on identical data; each stamp explains its figure")
    void differentPoliciesDifferentFiguresEachExplained() throws SQLException {
        UUID employeeOfA = employeeWithLopDays(TENANT_A);
        PayFigureExplanationResponse a = computeAndExplain(TENANT_A, employeeOfA);
        UUID employeeOfB = employeeWithLopDays(TENANT_B);
        PayFigureExplanationResponse b = computeAndExplain(TENANT_B, employeeOfB);

        assertThat(a.lopAmount()).isEqualByComparingTo("2056.45");
        assertThat(b.lopAmount()).isEqualByComparingTo("2451.92");
        assertThat(a.lopAmount()).isNotEqualByComparingTo(b.lopAmount());

        assertThat(a.lopPolicyId()).isEqualTo(PayRunTestSchema.policyId(TENANT_A));
        assertThat(a.workingDayBasis()).isEqualTo(WorkingDayBasis.ACTUAL_DAYS);
        assertThat(a.payDivisor()).isEqualByComparingTo("31.00");
        assertThat(b.lopPolicyId()).isEqualTo(PayRunTestSchema.policyId(TENANT_B));
        assertThat(b.workingDayBasis()).isEqualTo(WorkingDayBasis.ORG_DAYS);
        assertThat(b.payDivisor()).isEqualByComparingTo("26.00");

        for (PayFigureExplanationResponse explained : List.of(a, b)) {
            assertThat(explained.lopRounding()).isEqualTo(LopRounding.HALF_UP_2);
            assertThat(explained.lopDays()).isEqualByComparingTo(LOP_DAYS);
            // The stamp alone reproduces the figure: base × LOP days ÷ stamped divisor, stamped rounding.
            BigDecimal fromStamp = PRO_RATA_BASE
                    .multiply(explained.lopDays())
                    .divide(explained.payDivisor(), 2, java.math.RoundingMode.HALF_UP);
            assertThat(explained.lopAmount()).isEqualByComparingTo(fromStamp);
        }
    }

    private UUID employeeWithLopDays(UUID tenant) throws SQLException {
        TenantContext.set(tenant);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        UUID employee = PayRunTestSchema.insertWorkedExampleEmployee(
                tenant, "T-01", PayRunTestSchema.insertWorkedExampleCatalogue(tenant));
        PayRunTestSchema.markWorkedExampleProRata(tenant);
        payInputService.record(new PayInputCommand(
                employee, JULY, PayInputKind.LOP_DAYS, LOP_DAYS, null, "hrms", "leave-" + UUID.randomUUID()));
        return employee;
    }

    private PayFigureExplanationResponse computeAndExplain(UUID tenant, UUID employee) {
        TenantContext.set(tenant);
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        assertThat(worker.computeNow(run.id()).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(tenant);
        return explanationService.explain(run.id(), employee);
    }
}
