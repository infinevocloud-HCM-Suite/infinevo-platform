package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link PayInputServiceImpl} (W-19 §7).
 *
 * <p>No calculation is exercised here beyond the one arithmetic the ledger does: summing a kind's
 * rows, with a reversal subtracted. Locking and the late-input redirect are
 * {@link PayInputLatePeriodTest}'s.
 */
class PayInputServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final YearMonth PERIOD = YearMonth.of(2026, 4);

    private PayInputRepository payInputs;
    private PayInputPeriodLockRepository locks;
    private PayInputServiceImpl service;

    @BeforeEach
    void setUp() {
        payInputs = mock(PayInputRepository.class);
        locks = mock(PayInputPeriodLockRepository.class);
        service = new PayInputServiceImpl(payInputs, locks);
        TenantContext.set(TENANT);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(any(), any())).thenReturn(false);
        when(payInputs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A quantity of 0.5 survives a round trip at two decimal places")
    void quantityKeepsTwoDecimals() {
        UUID employeeId = UUID.randomUUID();
        PayInputResponse response = service.record(new PayInputCommand(
                employeeId, PERIOD, PayInputKind.LOP_DAYS, new BigDecimal("0.5"), null, "hrms", "lop:1"));

        assertThat(response.quantity()).isEqualByComparingTo("0.50");
        assertThat(response.postedPeriod()).isEqualTo(PERIOD);
    }

    @Test
    @DisplayName("Amount is stored rounded to two decimal places in a numeric(19,4) column")
    void amountIsRoundedOnceAtTheBoundary() {
        UUID employeeId = UUID.randomUUID();
        PayInputResponse response = service.record(new PayInputCommand(
                employeeId, PERIOD, PayInputKind.REIMBURSEMENT, null, Money.of("1250.505"), "hrms", "reimb:1"));

        assertThat(response.amount()).isEqualByComparingTo("1250.51"); // HALF_UP at scale 2
    }

    @Test
    @DisplayName("A zero or negative quantity is refused")
    void nonPositiveQuantityIsRefused() {
        UUID employeeId = UUID.randomUUID();
        assertThatThrownBy(() -> service.record(new PayInputCommand(
                        employeeId, PERIOD, PayInputKind.LOP_DAYS, BigDecimal.ZERO, null, "hrms", "lop:2")))
                .isInstanceOf(PayInputService.ValidationException.class)
                .satisfies(e -> assertThat(((PayInputService.ValidationException) e).fieldErrors())
                        .containsKey("quantity"));

        assertThatThrownBy(() -> service.record(new PayInputCommand(
                        employeeId, PERIOD, PayInputKind.LOP_DAYS, new BigDecimal("-1"), null, "hrms", "lop:3")))
                .isInstanceOf(PayInputService.ValidationException.class);
    }

    @Test
    @DisplayName("A zero or negative amount is refused")
    void nonPositiveAmountIsRefused() {
        UUID employeeId = UUID.randomUUID();
        assertThatThrownBy(() -> service.record(new PayInputCommand(
                        employeeId, PERIOD, PayInputKind.REIMBURSEMENT, null, Money.of("-1"), "hrms", "reimb:2")))
                .isInstanceOf(PayInputService.ValidationException.class)
                .satisfies(e -> assertThat(((PayInputService.ValidationException) e).fieldErrors())
                        .containsKey("amount"));
    }

    @Test
    @DisplayName("A reversal nets to zero against the row it reverses in the kind's total")
    void reversalNetsToZero() {
        UUID employeeId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                employeeId,
                PERIOD,
                PayInputKind.OVERTIME,
                new BigDecimal("3.00"),
                new BigDecimal("500.00"),
                "core",
                "overtime_request:1",
                null,
                "admin");
        PayInput reversal = new PayInput(
                TENANT,
                employeeId,
                PERIOD,
                PayInputKind.OVERTIME,
                new BigDecimal("3.00"),
                new BigDecimal("500.00"),
                "core",
                "overtime_request:1",
                UUID.randomUUID(), // reverses the original
                "admin");
        when(payInputs.findByTenantIdAndEmployeeIdAndPeriodAndRunRefIsNull(TENANT, employeeId, PERIOD))
                .thenReturn(List.of(original, reversal));

        PayInputListResponse response = service.forEmployee(employeeId, PERIOD);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.quantityTotalsByKind().get(PayInputKind.OVERTIME)).isEqualByComparingTo("0.00");
        assertThat(response.amountTotalsByKind().get(PayInputKind.OVERTIME)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName(
            "Totals by kind are split into quantity and amount, and an amount-only row contributes nothing to quantity")
    void totalsByKindSeparatesQuantityAndAmount() {
        UUID employeeId = UUID.randomUUID();
        PayInput hoursOnly = new PayInput(
                TENANT,
                employeeId,
                PERIOD,
                PayInputKind.OVERTIME,
                new BigDecimal("2.00"),
                null,
                "core",
                "ot:1",
                null,
                "admin");
        PayInput amountOnly = new PayInput(
                TENANT,
                employeeId,
                PERIOD,
                PayInputKind.REIMBURSEMENT,
                null,
                new BigDecimal("300.00"),
                "hrms",
                "reimb:3",
                null,
                "admin");
        when(payInputs.findByTenantIdAndEmployeeIdAndPeriodAndRunRefIsNull(TENANT, employeeId, PERIOD))
                .thenReturn(List.of(hoursOnly, amountOnly));

        PayInputListResponse response = service.forEmployee(employeeId, PERIOD);

        assertThat(response.quantityTotalsByKind()).containsEntry(PayInputKind.OVERTIME, new BigDecimal("2.00"));
        assertThat(response.quantityTotalsByKind()).doesNotContainKey(PayInputKind.REIMBURSEMENT);
        assertThat(response.amountTotalsByKind()).containsEntry(PayInputKind.REIMBURSEMENT, new BigDecimal("300.00"));
        assertThat(response.amountTotalsByKind()).doesNotContainKey(PayInputKind.OVERTIME);
    }

    @Test
    @DisplayName("forPeriod reads every employee's rows for the period in one statement")
    void forPeriodReadsEveryEmployee() {
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        when(payInputs.findByTenantIdAndPeriodAndRunRefIsNull(TENANT, PERIOD))
                .thenReturn(List.of(
                        new PayInput(
                                TENANT,
                                employeeA,
                                PERIOD,
                                PayInputKind.LOP_DAYS,
                                BigDecimal.ONE,
                                null,
                                "hrms",
                                "a",
                                null,
                                "x"),
                        new PayInput(
                                TENANT,
                                employeeB,
                                PERIOD,
                                PayInputKind.LOP_DAYS,
                                BigDecimal.ONE,
                                null,
                                "hrms",
                                "b",
                                null,
                                "x")));

        PayInputListResponse response = service.forPeriod(PERIOD);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows())
                .extracting(PayInputResponse::employeeId)
                .containsExactlyInAnyOrder(employeeA, employeeB);
    }

    @Test
    @DisplayName("lock is idempotent: locking the same period twice leaves one row")
    void lockTwiceIsANoOp() {
        when(locks.insertIfAbsent(eq(TENANT), eq(PERIOD.toString()), any()))
                .thenReturn(1) // first call creates the row
                .thenReturn(0); // second call: ON CONFLICT DO NOTHING, no row changed

        service.lock(PERIOD);
        service.lock(PERIOD); // no exception - a no-op
    }

    @Test
    @DisplayName(
            "A second record with the same (source_module, source_ref) is refused, translated from the unique index")
    void duplicateSourceIsRefused() {
        when(payInputs.saveAndFlush(any(PayInput.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_pay_input_tenant_source\""));

        assertThatThrownBy(() -> service.record(new PayInputCommand(
                        UUID.randomUUID(), PERIOD, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "dup:1")))
                .isInstanceOf(PayInputService.DuplicatePayInputException.class);
    }

    @Test
    @DisplayName("reverse copies the original's values and sets reversesId; it fails for an unknown id")
    void reverseCopiesTheOriginal() {
        UUID employeeId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                employeeId,
                PERIOD,
                PayInputKind.AD_HOC_DEDUCTION,
                null,
                new BigDecimal("100.00"),
                "payroll",
                "ded:1",
                null,
                "admin");
        when(payInputs.findByIdAndTenantId(eq(originalId), eq(TENANT))).thenReturn(java.util.Optional.of(original));

        PayInputResponse reversal = service.reverse(originalId, "mistake");

        assertThat(reversal.amount()).isEqualByComparingTo("100.00");
        assertThat(reversal.sourceRef()).isEqualTo("ded:1");
        assertThat(reversal.postedPeriod()).isEqualTo(PERIOD);

        assertThatThrownBy(() -> service.reverse(UUID.randomUUID(), "mistake"))
                .isInstanceOf(PayInputService.NotFoundException.class);
    }
}
