package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.LopFixtures.context;
import static com.infinevo.payroll.payrun.LopFixtures.days;
import static com.infinevo.payroll.payrun.LopFixtures.input;
import static com.infinevo.payroll.payrun.LopFixtures.structure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.3 §7 — the LOP contributor and the day figures behind it. */
class LopLineContributorTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);
    private static final UUID BASIC = UUID.randomUUID();
    private static final UUID ALLOWANCE = UUID.randomUUID();
    private static final UUID GRATUITY = UUID.randomUUID();
    private static final UUID FUEL = UUID.randomUUID();

    private final LopLineContributor contributor = new LopLineContributor();

    @Test
    @DisplayName("Full month, no LOP: no line, and paid days equal payable days")
    void fullMonthNoLine() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(contributor.contribute(ctx)).isEmpty();
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("31");
        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("1.5 LOP days under FIXED_30: 30,000 × 1.5 ÷ 30 = 1,500.00")
    void halfDayUnderFixed30() {
        PayRunEmployeeContext ctx = context(
                JUNE,
                days("30"),
                days("30"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(input(PayInputKind.LOP_DAYS, "1.5", null)),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "30000")));

        assertThat(ctx.days().lopDays()).isEqualByComparingTo("1.50");
        assertThat(contributor.contribute(ctx))
                .extracting(PayLine::kind, PayLine::source, PayLine::componentCode, l -> l.amount()
                        .raw())
                .containsExactly(tuple(LineKind.DEDUCTION, LineSource.LOP, "LOP", new BigDecimal("1500.0000")));
    }

    @Test
    @DisplayName("Joiner on the 16th under ACTUAL_DAYS in a 31-day month: 15 unpaid days")
    void joinerOnTheSixteenth() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 7, 16),
                null,
                List.of(),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("15");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("16");
        assertThat(contributor.contribute(ctx)).singleElement().satisfies(l -> assertThat(l.amount())
                .isEqualTo(Money.of("15000")));
    }

    @Test
    @DisplayName("Leaver on the 10th: 21 unpaid days — legacy paid the whole month")
    void leaverOnTheTenth() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                LocalDate.of(2026, 7, 10),
                List.of(),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("21");
        assertThat(contributor.contribute(ctx)).singleElement().satisfies(l -> assertThat(l.amount())
                .isEqualTo(Money.of("21000")));
    }

    @Test
    @DisplayName("LOP days are capped at payable days, and the line never exceeds its base")
    void lopCappedAtPayableDays() {
        PayRunEmployeeContext ctx = context(
                JUNE,
                days("30"),
                days("30"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(input(PayInputKind.LOP_DAYS, "40", null)),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "30000")));

        assertThat(ctx.days().lopDays()).isEqualByComparingTo("30");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("0");
        assertThat(contributor.contribute(ctx)).singleElement().satisfies(l -> assertThat(l.amount())
                .isEqualTo(Money.of("30000")));
    }

    @Test
    @DisplayName(
            "Only pro-rata lines are in the base: the benefit is scaled, the reimbursement and a fixed allowance are not")
    void onlyProRataLinesScale() {
        PayRunEmployeeContext ctx = context(
                JUNE,
                days("30"),
                days("30"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(input(PayInputKind.LOP_DAYS, "3", null)),
                Set.of(BASIC, GRATUITY),
                List.of(
                        structure(LineKind.EARNING, BASIC, "BASIC", "30000"),
                        structure(LineKind.EARNING, ALLOWANCE, "ALLOWANCE", "9000"),
                        structure(LineKind.BENEFIT, GRATUITY, "GRATUITY", "1500"),
                        structure(LineKind.REIMBURSEMENT, FUEL, "FUEL", "2000")));

        assertThat(contributor.contribute(ctx))
                .extracting(
                        PayLine::kind, PayLine::componentCode, l -> l.amount().raw())
                .containsExactly(
                        tuple(LineKind.DEDUCTION, "LOP", new BigDecimal("3000.0000")),
                        tuple(LineKind.BENEFIT, "LOP_BENEFIT", new BigDecimal("150.0000")));
    }

    @Test
    @DisplayName("Rounding follows lop_rounding: HALF_UP_2, HALF_UP_0, NONE")
    void roundingPerPolicy() {
        List<PayLine> prior = List.of(structure(LineKind.EARNING, BASIC, "BASIC", "10000"));
        var lop = List.of(input(PayInputKind.LOP_DAYS, "1", null));

        assertThat(amount(context(
                        JULY,
                        days("31"),
                        days("31"),
                        LopRounding.HALF_UP_2,
                        LONG_AGO,
                        null,
                        lop,
                        Set.of(BASIC),
                        prior)))
                .isEqualByComparingTo("322.5800");
        assertThat(amount(context(
                        JULY,
                        days("31"),
                        days("31"),
                        LopRounding.HALF_UP_0,
                        LONG_AGO,
                        null,
                        lop,
                        Set.of(BASIC),
                        prior)))
                .isEqualByComparingTo("323");
        assertThat(amount(context(
                        JULY, days("31"), days("31"), LopRounding.NONE, LONG_AGO, null, lop, Set.of(BASIC), prior)))
                .isEqualByComparingTo("322.5806");
    }

    @Test
    @DisplayName("A LOP reversal landing in a later period is paid back: 31,000 × 2 ÷ 31 = 2,000.00, and the benefit")
    void reversalFromAPaidMonthIsRefunded() {
        List<PayLine> prior = List.of(
                structure(LineKind.EARNING, BASIC, "BASIC", "31000"),
                structure(LineKind.BENEFIT, GRATUITY, "GRATUITY", "1550"));
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                // The row it reverses was deducted in June, which is locked; W-19 posted this one to July.
                List.of(input(PayInputKind.LOP_DAYS, "2", null, UUID.randomUUID())),
                Set.of(BASIC, GRATUITY),
                prior);

        assertThat(ctx.days().lopDays()).isEqualByComparingTo("0");
        assertThat(ctx.days().creditDays()).isEqualByComparingTo("2");
        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("0");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("31");
        List<PayLine> lines = contributor.contribute(ctx);
        assertThat(lines)
                .extracting(PayLine::kind, PayLine::source, PayLine::componentCode, PayLine::taxable, l -> l.amount()
                        .raw())
                .containsExactly(
                        tuple(LineKind.EARNING, LineSource.LOP, "LOP_REVERSAL", true, new BigDecimal("2000.0000")),
                        tuple(
                                LineKind.BENEFIT,
                                LineSource.LOP,
                                "LOP_BENEFIT_REVERSAL",
                                false,
                                new BigDecimal("100.0000")));

        List<PayLine> all = new ArrayList<>(prior);
        all.addAll(lines);
        PayRunTotals totals = PayRunTotals.of(all);
        assertThat(totals.grossEarnings().raw()).isEqualByComparingTo("33000");
        assertThat(totals.totalBenefits().raw()).isEqualByComparingTo("1650");
        assertThat(totals.netPay()).isEqualTo(new BigDecimal("33000.00"));
    }

    @Test
    @DisplayName("Reversals of two locked months landing together are all paid back: 31,000 × 40 ÷ 31 = 40,000.00")
    void refundIsNotCappedAtOneMonth() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(
                        input(PayInputKind.LOP_DAYS, "20", null, UUID.randomUUID()),
                        input(PayInputKind.LOP_DAYS, "20", null, UUID.randomUUID())),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(ctx.days().creditDays()).isEqualByComparingTo("40");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("31");
        assertThat(contributor.contribute(ctx))
                .extracting(
                        PayLine::kind, PayLine::componentCode, l -> l.amount().raw())
                .containsExactly(tuple(LineKind.EARNING, "LOP_REVERSAL", new BigDecimal("40000.0000")));
    }

    @Test
    @DisplayName("New LOP days and an older reversal in one period net first: 3 − 2 = 1 day deducted, nothing refunded")
    void reversalNetsAgainstNewLopDays() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                null,
                List.of(
                        input(PayInputKind.LOP_DAYS, "3", null),
                        input(PayInputKind.LOP_DAYS, "2", null, UUID.randomUUID())),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(ctx.days().lopDays()).isEqualByComparingTo("1");
        assertThat(ctx.days().creditDays()).isEqualByComparingTo("0");
        assertThat(contributor.contribute(ctx))
                .extracting(
                        PayLine::kind, PayLine::componentCode, l -> l.amount().raw())
                .containsExactly(tuple(LineKind.DEDUCTION, "LOP", new BigDecimal("1000.0000")));
    }

    @Test
    @DisplayName(
            "A leaver owed a refund gets both lines: days after the last day deducted, the reversed days paid back")
    void leaverWithARefund() {
        PayRunEmployeeContext ctx = context(
                JULY,
                days("31"),
                days("31"),
                LopRounding.HALF_UP_2,
                LONG_AGO,
                LocalDate.of(2026, 7, 10),
                List.of(input(PayInputKind.LOP_DAYS, "2", null, UUID.randomUUID())),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "31000")));

        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("21");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("10");
        assertThat(contributor.contribute(ctx))
                .extracting(
                        PayLine::kind, PayLine::componentCode, l -> l.amount().raw())
                .containsExactly(
                        tuple(LineKind.DEDUCTION, "LOP", new BigDecimal("21000.0000")),
                        tuple(LineKind.EARNING, "LOP_REVERSAL", new BigDecimal("2000.0000")));
    }

    private BigDecimal amount(PayRunEmployeeContext ctx) {
        return contributor.contribute(ctx).get(0).amount().raw();
    }
}
