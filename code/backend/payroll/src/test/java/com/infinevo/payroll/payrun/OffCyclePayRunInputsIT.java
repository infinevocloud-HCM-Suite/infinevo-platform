package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
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
 * W-30.2 §7 — {@code POST /inputs}: ledger rows tagged with the run and a prefixed reference; a retry
 * is {@code DUPLICATE} and writes nothing; anyone not included, {@code LOP_DAYS}, a non-positive
 * amount or a regular run is refused before anything is written; after lock the run refuses and so
 * does the ledger's own trigger.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OffCyclePayRunInputsIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final LocalDate MID_APRIL = LocalDate.of(2026, 4, 15);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

    private UUID first;
    private UUID second;
    private UUID noBank;
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
        first = PayRunTestSchema.insertPayableEmployee(TENANT_A, "I-01");
        second = PayRunTestSchema.insertPayableEmployee(TENANT_A, "I-02");
        noBank = PayRunTestSchema.insertEmployee(TENANT_A, "I-03", LocalDate.of(2025, 1, 1), "ACTIVE", null);
        runId = payRunService
                .createOffCycle(MID_APRIL, List.of(first, second, noBank), null)
                .id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Inputs are ledger rows tagged with the run, references prefixed; the regular read never sees them")
    void inputsAreTaggedRows() throws SQLException {
        List<PayRunInputResponse> results = payRunService.addInputs(
                runId,
                List.of(
                        input(first, PayInputKind.ONE_TIME_PAYOUT, "10000", "bonus-1"),
                        input(second, PayInputKind.AD_HOC_DEDUCTION, "500", "recovery-1")));

        assertThat(results)
                .extracting(PayRunInputResponse::result)
                .containsExactly(PayRunInputResponse.Result.RECORDED, PayRunInputResponse.Result.RECORDED);
        assertThat(results).allSatisfy(r -> assertThat(r.payInputId()).isNotNull());

        List<PayInputResponse> rows = payInputService.forRun(runId).rows();
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.runRef()).isEqualTo(runId);
            assertThat(row.sourceModule()).isEqualTo("payroll");
            assertThat(row.sourceRef()).startsWith("payrun:" + runId + ":");
            assertThat(row.postedPeriod()).isEqualTo(APRIL);
        });
        assertThat(rows)
                .extracting(PayInputResponse::sourceRef)
                .containsExactlyInAnyOrder("payrun:" + runId + ":bonus-1", "payrun:" + runId + ":recovery-1");
        assertThat(payInputService.forPeriod(APRIL).rows()).isEmpty();
    }

    @Test
    @DisplayName("The same POST again reports DUPLICATE per item and writes nothing; a new item beside it is recorded")
    void retryIsDuplicate() throws SQLException {
        payRunService.addInputs(runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "10000", "bonus-1")));

        List<PayRunInputResponse> again = payRunService.addInputs(
                runId,
                List.of(
                        input(first, PayInputKind.ONE_TIME_PAYOUT, "10000", "bonus-1"),
                        input(second, PayInputKind.ONE_TIME_PAYOUT, "2000", "bonus-2")));

        assertThat(again)
                .extracting(PayRunInputResponse::result)
                .containsExactly(PayRunInputResponse.Result.DUPLICATE, PayRunInputResponse.Result.RECORDED);
        assertThat(again.get(0).payInputId()).isNull();
        assertThat(PayRunTestSchema.countTaggedInputs(TENANT_A, runId)).isEqualTo(2);
    }

    @Test
    @DisplayName("The same reference on another off-cycle run is another row")
    void sameReferenceOnTwoRuns() throws SQLException {
        UUID other =
                payRunService.createOffCycle(MID_APRIL, List.of(first), null).id();

        payRunService.addInputs(runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "100", "bonus-1")));
        List<PayRunInputResponse> onOther =
                payRunService.addInputs(other, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "100", "bonus-1")));

        assertThat(onOther.get(0).result()).isEqualTo(PayRunInputResponse.Result.RECORDED);
        assertThat(PayRunTestSchema.countTaggedInputs(TENANT_A, runId)).isEqualTo(1);
        assertThat(PayRunTestSchema.countTaggedInputs(TENANT_A, other)).isEqualTo(1);
    }

    @Test
    @DisplayName("Refused with nothing written: not in the run, skipped, LOP_DAYS, amount ≤ 0, no or long reference")
    void badItemsWriteNothing() throws SQLException {
        UUID stranger = PayRunTestSchema.insertPayableEmployee(TENANT_A, "I-09");
        PayRunInputRequest good = input(first, PayInputKind.ONE_TIME_PAYOUT, "100", "ok");

        assertThatThrownBy(() -> payRunService.addInputs(
                        runId, List.of(good, input(stranger, PayInputKind.ONE_TIME_PAYOUT, "100", "x"))))
                .isInstanceOf(EmployeeNotInRunException.class)
                .hasMessageContaining(stranger.toString());
        assertThatThrownBy(() -> payRunService.addInputs(
                        runId, List.of(input(noBank, PayInputKind.ONE_TIME_PAYOUT, "100", "x"))))
                .isInstanceOf(EmployeeNotInRunException.class);
        assertThatThrownBy(() ->
                        payRunService.addInputs(runId, List.of(good, input(first, PayInputKind.LOP_DAYS, "1", "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOP_DAYS");
        assertThatThrownBy(() ->
                        payRunService.addInputs(runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "0", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        payRunService.addInputs(runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "-5", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        payRunService.addInputs(runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "5", " "))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payRunService.addInputs(
                        runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "5", "r".repeat(21)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payRunService.addInputs(runId, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(PayRunTestSchema.countTaggedInputs(TENANT_A, runId)).isZero();
    }

    @Test
    @DisplayName("A regular run refuses inputs (409)")
    void regularRunRefuses() {
        UUID regular = payRunService.create(APRIL).id();

        assertThatThrownBy(() -> payRunService.addInputs(
                        regular, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "100", "x"))))
                .isInstanceOf(NotAnOffCycleRunException.class);
    }

    @Test
    @DisplayName("After lock: the run refuses (409) and the ledger trigger refuses a direct insert tagged with it")
    void lockedRunRefuses() {
        payRunService.lock(runId);

        assertThatThrownBy(() -> payRunService.addInputs(
                        runId, List.of(input(first, PayInputKind.ONE_TIME_PAYOUT, "100", "late"))))
                .isInstanceOf(NotAnOffCycleRunException.class);
        assertThatThrownBy(() -> PayRunTestSchema.execute(
                        "INSERT INTO core.pay_input (tenant_id, employee_id, period, kind, amount, source_module, "
                                + "source_ref, run_ref) VALUES (?, ?, '2026-04', 'ONE_TIME_PAYOUT', 100, 'test', "
                                + "'direct', ?)",
                        TENANT_A,
                        first,
                        runId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("is locked");
    }

    private static PayRunInputRequest input(UUID employeeId, PayInputKind kind, String amount, String ref) {
        return new PayRunInputRequest(employeeId, kind, new BigDecimal(amount), ref);
    }
}
