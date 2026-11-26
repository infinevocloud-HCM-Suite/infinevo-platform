package com.infinevo.payroll.challan;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
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
 * W-89 over the real schema ({@code V225}) and a real July 2026 run paid on 31 July (so Q2 2026-27):
 * another tenant cannot see a challan, allocation is capped by the TDS actually on the run, and a filed quarter
 * locks its challans until reopened.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TdsChallanIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private TdsChallanService challanService;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    private UUID employee;
    private UUID runId;

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
        employee = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "T-01", catalogue);
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        assertThat(worker.computeNow(run.id()).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);
        assertThat(payRunService.approve(run.id()).status()).isEqualTo(PayRunStatus.APPROVED);
        // TDS is deducted when salary is paid: only a paid run's TDS is allocated.
        assertThat(payRunService.pay(run.id(), LocalDate.of(2026, 7, 31)).status())
                .isEqualTo(PayRunStatus.PAID);
        runId = run.id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TdsChallanRequest q2(String serial, String tax) {
        return new TdsChallanRequest(
                "2026-2027",
                "Q2",
                "0510304",
                serial,
                LocalDate.of(2026, 8, 7),
                new BigDecimal(tax),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                null);
    }

    @Test
    @DisplayName("a challan is the recording tenant's only: tenant B neither lists nor finds it")
    void challansAreTenantIsolated() {
        TdsChallanResponse saved = challanService.create(q2("12", "5000"));
        assertThat(challanService.list("2026-2027", "Q2"))
                .extracting(TdsChallanResponse::id)
                .containsExactly(saved.id());

        TenantContext.set(TENANT_B);
        assertThat(challanService.list("2026-2027", "Q2")).isEmpty();
        assertThat(challanService.findById(saved.id())).isEmpty();
        assertThatThrownBy(() -> challanService.allocate(
                        saved.id(),
                        new ChallanAllocationRequest(
                                runId,
                                List.of(new ChallanAllocationRequest.EmployeeAllocationItem(
                                        employee, BigDecimal.ONE, null, null)))))
                .isInstanceOf(TdsChallanNotFoundException.class);
    }

    @Test
    @DisplayName("allocation is capped by the TDS on the run, and the July run counts in Q2, not Q1")
    void allocationCappedByRealTds() {
        TdsChallanResponse challan = challanService.create(q2("13", "999999"));
        List<AllocationCandidate> candidates = challanService.candidates(challan.id());
        BigDecimal tds = candidates.stream()
                .filter(c -> c.employeeId().equals(employee))
                .map(AllocationCandidate::tdsDeducted)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        BigDecimal tooMuch = tds.add(BigDecimal.ONE);
        assertThatThrownBy(() -> challanService.allocate(
                        challan.id(),
                        new ChallanAllocationRequest(
                                runId,
                                List.of(new ChallanAllocationRequest.EmployeeAllocationItem(
                                        employee, tooMuch, null, null)))))
                .isInstanceOf(TdsChallanValidationException.class);

        if (tds.signum() > 0) {
            TdsChallanResponse after = challanService.allocate(
                    challan.id(),
                    new ChallanAllocationRequest(
                            runId,
                            List.of(new ChallanAllocationRequest.EmployeeAllocationItem(employee, tds, null, null))));
            assertThat(after.allocatedAmount()).isEqualByComparingTo(tds);
            assertThat(challanService.listAllocations(challan.id())).hasSize(1);
        }

        TdsChallanResponse q1 = challanService.create(new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0510304",
                "14",
                LocalDate.of(2026, 6, 7),
                new BigDecimal("100"),
                null,
                null,
                null,
                null,
                "200",
                null));
        assertThat(challanService.candidates(q1.id())).isEmpty();
        assertThatThrownBy(() -> challanService.allocate(
                        q1.id(),
                        new ChallanAllocationRequest(
                                runId,
                                List.of(new ChallanAllocationRequest.EmployeeAllocationItem(
                                        employee, BigDecimal.ONE, null, null)))))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("Q2");
    }

    @Test
    @DisplayName("a filed quarter locks its challans; reopening unlocks them")
    void filedQuarterLocks() {
        TdsChallanResponse challan = challanService.create(q2("15", "5000"));
        TdsReturnResponse filed = challanService.markFiled(
                "2026-2027", "Q2", new TdsReturnRequest("123456789012345", LocalDate.of(2026, 10, 30)));
        assertThat(filed.tokenNumber()).isEqualTo("123456789012345");
        assertThat(challanService.findById(challan.id()).orElseThrow().filed()).isTrue();

        assertThatThrownBy(() -> challanService.update(challan.id(), q2("15", "6000")))
                .isInstanceOf(TdsChallanValidationException.class);
        assertThatThrownBy(() -> challanService.delete(challan.id())).isInstanceOf(TdsChallanValidationException.class);
        assertThatThrownBy(() -> challanService.create(q2("16", "100")))
                .isInstanceOf(TdsChallanValidationException.class);

        challanService.reopen("2026-2027", "Q2");
        assertThat(challanService.update(challan.id(), q2("15", "6000")).taxAmount())
                .isEqualByComparingTo("6000");
        assertThat(challanService.returns("2026-2027")).isEmpty();
    }
}
