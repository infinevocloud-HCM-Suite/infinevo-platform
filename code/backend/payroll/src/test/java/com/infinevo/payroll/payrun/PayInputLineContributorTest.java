package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.LopFixtures.context;
import static com.infinevo.payroll.payrun.LopFixtures.days;
import static com.infinevo.payroll.payrun.LopFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.3 §7 — the PAY_INPUT contributor over one employee's slice of the ledger. */
class PayInputLineContributorTest {

    private final PayInputLineContributor contributor = new PayInputLineContributor();

    @Test
    @DisplayName("One line per kind; two reimbursement rows sum to one line; LOP_DAYS writes no money line")
    void oneLinePerKind() {
        List<PayLine> lines = contribute(List.of(
                input(PayInputKind.OVERTIME, "4", "1200"),
                input(PayInputKind.ONE_TIME_PAYOUT, "1", "5000"),
                input(PayInputKind.REIMBURSEMENT, "1", "300"),
                input(PayInputKind.REIMBURSEMENT, "1", "450.50"),
                input(PayInputKind.AD_HOC_DEDUCTION, "1", "250"),
                input(PayInputKind.LOP_DAYS, "2", null)));

        assertThat(lines)
                .extracting(
                        PayLine::kind,
                        PayLine::source,
                        PayLine::componentCode,
                        l -> l.amount().raw(),
                        PayLine::taxable)
                .containsExactly(
                        tuple(LineKind.EARNING, LineSource.PAY_INPUT, "OVERTIME", new BigDecimal("1200.0000"), true),
                        tuple(
                                LineKind.EARNING,
                                LineSource.PAY_INPUT,
                                "ONE_TIME_PAYOUT",
                                new BigDecimal("5000.0000"),
                                true),
                        tuple(
                                LineKind.REIMBURSEMENT,
                                LineSource.PAY_INPUT,
                                "REIMBURSEMENT",
                                new BigDecimal("750.5000"),
                                false),
                        tuple(
                                LineKind.DEDUCTION,
                                LineSource.PAY_INPUT,
                                "AD_HOC_DEDUCTION",
                                new BigDecimal("250.0000"),
                                false));
    }

    @Test
    @DisplayName("A reversal pair nets to nothing — for money and for LOP days")
    void reversalPairNetsToNothing() {
        PayInputResponse payout = input(PayInputKind.ONE_TIME_PAYOUT, "1", "5000");
        PayInputResponse lop = input(PayInputKind.LOP_DAYS, "2", null);
        List<PayInputResponse> inputs = List.of(
                payout,
                input(PayInputKind.ONE_TIME_PAYOUT, "1", "5000", payout.id()),
                lop,
                input(PayInputKind.LOP_DAYS, "2", null, lop.id()));

        assertThat(contribute(inputs)).isEmpty();
        assertThat(PayInputLineContributor.netLopDays(inputs)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Overtime with hours and no amount: no line, counted as unpriced")
    void unpricedOvertime() {
        List<PayInputResponse> inputs = List.of(input(PayInputKind.OVERTIME, "6", null));

        assertThat(contribute(inputs)).isEmpty();
        assertThat(PayInputLineContributor.unpricedCount(inputs)).isEqualTo(1);
    }

    @Test
    @DisplayName("A reversal posted to a later period than its original is written on the other side")
    void negativeNetIsWrittenOnTheOtherSide() {
        List<PayLine> lines = contribute(List.of(
                input(PayInputKind.REIMBURSEMENT, "1", "800", java.util.UUID.randomUUID()),
                input(PayInputKind.AD_HOC_DEDUCTION, "1", "100", java.util.UUID.randomUUID())));

        assertThat(lines)
                .extracting(
                        PayLine::kind, PayLine::componentCode, l -> l.amount().raw())
                .containsExactly(
                        tuple(LineKind.DEDUCTION, "REIMBURSEMENT_REVERSAL", new BigDecimal("800.0000")),
                        tuple(LineKind.EARNING, "AD_HOC_DEDUCTION_REVERSAL", new BigDecimal("100.0000")));
    }

    private List<PayLine> contribute(List<PayInputResponse> inputs) {
        return contributor.contribute(context(
                YearMonth.of(2026, 7),
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LocalDate.of(2020, 1, 1),
                null,
                inputs,
                Set.of(),
                List.of()));
    }
}
