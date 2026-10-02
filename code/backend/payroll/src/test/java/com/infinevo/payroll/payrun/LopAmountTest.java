package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.LopFixtures.context;
import static com.infinevo.payroll.payrun.LopFixtures.days;
import static com.infinevo.payroll.payrun.LopFixtures.input;
import static com.infinevo.payroll.payrun.LopFixtures.structure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-18.2 §7 — the loss-of-pay amount divides by the calculator's divisor, not the period's calendar
 * days: the same two LOP days on the same 26,000 basic give three different amounts under
 * {@code ACTUAL_DAYS} (31 in July), {@code ORG_DAYS} with 26 configured, and {@code FIXED_30}; and the
 * amount is rounded by the stamped rule, with no default when none is given.
 */
class LopAmountTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);
    private static final UUID BASIC = UUID.randomUUID();

    private final LopLineContributor contributor = new LopLineContributor();

    @Test
    @DisplayName("Two LOP days on 26,000: ACTUAL_DAYS 1,677.42 · ORG_DAYS(26) 2,000.00 · FIXED_30 1,733.33")
    void theDivisorIsThePolicysNotTheCalendars() {
        assertThat(lop(days("31"), LopRounding.HALF_UP_2)).isEqualByComparingTo("1677.42");
        assertThat(lop(days("26"), LopRounding.HALF_UP_2)).isEqualByComparingTo("2000.00");
        assertThat(lop(days("30"), LopRounding.HALF_UP_2)).isEqualByComparingTo("1733.33");
    }

    @Test
    @DisplayName("The stamped rounding decides the amount: HALF_UP_2 1,677.42 · HALF_UP_0 1,677 · NONE unrounded")
    void roundingFollowsTheStampedRule() {
        assertThat(lop(days("31"), LopRounding.HALF_UP_2)).isEqualByComparingTo("1677.42");
        assertThat(lop(days("31"), LopRounding.HALF_UP_0)).isEqualByComparingTo("1677");
        assertThat(lop(days("31"), LopRounding.NONE))
                .isGreaterThan(new BigDecimal("1677.419"))
                .isLessThan(new BigDecimal("1677.420"));
    }

    @Test
    @DisplayName("No rounding rule is not HALF_UP_2 by default: it is refused")
    void noRuleIsRefused() {
        assertThatThrownBy(() -> LopLineContributor.round(Money.of("1677.4193"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("A 42,500 joiner on 16 July loses 15 of 31 days, 20,564.52, on any fixed basis: rounded once")
    void aJoinersShareIsNotRoundedBeforeTheMoney() {
        // Days rounded first gave 20,570.00 on 14.52 of 30, and 20,563.46 on 12.58 of 26.
        assertThat(joinerLop(days("30"))).isEqualByComparingTo("20564.52");
        assertThat(joinerLop(days("26"))).isEqualByComparingTo("20564.52");
        assertThat(joinerLop(days("31"))).isEqualByComparingTo("20564.52");
    }

    @Test
    @DisplayName("The row still records the days at scale 2: 14.52 unpaid and 15.48 paid of 30")
    void theRecordedDaysStayAtScaleTwo() {
        PayRunDays days = joiner(days("30")).days();

        assertThat(days.unpaidDays()).isEqualTo(new BigDecimal("14.52"));
        assertThat(days.paidDays()).isEqualTo(new BigDecimal("15.48"));
    }

    private BigDecimal joinerLop(BigDecimal divisor) {
        return lopOf(joiner(divisor));
    }

    private PayRunEmployeeContext joiner(BigDecimal divisor) {
        return context(
                JULY,
                divisor,
                divisor,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 7, 16),
                null,
                List.of(),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "42500")));
    }

    private BigDecimal lop(BigDecimal divisor, LopRounding rounding) {
        return lopOf(context(
                JULY,
                divisor,
                divisor,
                rounding,
                LONG_AGO,
                null,
                List.of(input(PayInputKind.LOP_DAYS, "2", null)),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "26000"))));
    }

    private BigDecimal lopOf(PayRunEmployeeContext ctx) {
        return contributor.contribute(ctx).stream()
                .filter(line -> line.kind() == LineKind.DEDUCTION && line.source() == LineSource.LOP)
                .map(line -> line.amount().raw())
                .findFirst()
                .orElseThrow();
    }
}
