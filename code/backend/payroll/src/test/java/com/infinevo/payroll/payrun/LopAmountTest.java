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

    private BigDecimal lop(BigDecimal divisor, LopRounding rounding) {
        PayRunEmployeeContext ctx = context(
                JULY,
                divisor,
                divisor,
                rounding,
                LONG_AGO,
                null,
                List.of(input(PayInputKind.LOP_DAYS, "2", null)),
                Set.of(BASIC),
                List.of(structure(LineKind.EARNING, BASIC, "BASIC", "26000")));
        return contributor.contribute(ctx).stream()
                .filter(line -> line.kind() == LineKind.DEDUCTION && line.source() == LineSource.LOP)
                .map(line -> line.amount().raw())
                .findFirst()
                .orElseThrow();
    }
}
