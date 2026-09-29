package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.HraExemption;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraExemptionResult;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxcalc.engine.HraExemption.RentPeriod;
import com.infinevo.payroll.taxcalc.reader.model.HraRule;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HraExemption} (W-33.2 spec § 7).
 */
class HraExemptionTest {

    private final HraRule rule =
            new HraRule("2025-2026", BigDecimal.valueOf(10.00), BigDecimal.valueOf(50.00), BigDecimal.valueOf(40.00));

    private List<HraMonthSalary> create12MonthsSalary(Money basicMonthly, Money hraMonthly) {
        List<HraMonthSalary> list = new ArrayList<>();
        YearMonth ym = YearMonth.of(2025, 4);
        for (int i = 0; i < 12; i++) {
            list.add(new HraMonthSalary(ym, basicMonthly, hraMonthly));
            ym = ym.plusMonths(1);
        }
        return list;
    }

    @Test
    @DisplayName("basic 50k, HRA 20k, rent 18k metro 12 months yields 1,56,000 exemption")
    void metro12MonthsStandard() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2026, 3), Money.of("18000"), true));

        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.of("156000"));
        assertThat(result.monthLines()).hasSize(12);
        assertThat(result.monthLines().get(0).exemption()).isEqualTo(Money.of("13000"));
    }

    @Test
    @DisplayName("non-metro yields same when rent minus 10% is below 40% cap")
    void nonMetro12Months() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2026, 3), Money.of("18000"), false));

        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.of("156000"));
    }

    @Test
    @DisplayName("rent period Oct-Mar only yields 6 months exemption (78,000)")
    void rentOctToMarOnly() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 10), YearMonth.of(2026, 3), Money.of("18000"), true));

        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.of("78000"));
        assertThat(result.monthLines().get(0).exemption()).isEqualTo(Money.ZERO);
        assertThat(result.monthLines().get(6).exemption()).isEqualTo(Money.of("13000"));
    }

    @Test
    @DisplayName("rent below 10% basic yields zero exemption")
    void rentBelowTenPercentBasic() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2026, 3), Money.of("4000"), true));

        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("flag isStayingInRentedHouse false yields zero exemption")
    void flagFalseYieldsZero() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2026, 3), Money.of("18000"), true));

        HraExemptionResult result = HraExemption.calculate(false, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("two non-overlapping rent periods add together correctly")
    void twoNonOverlappingPeriodsAdd() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.of("20000"));
        List<RentPeriod> rentPeriods = List.of(
                new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2025, 9), Money.of("15000"), true),
                new RentPeriod(YearMonth.of(2025, 10), YearMonth.of(2026, 3), Money.of("18000"), true));

        // Period 1: rent 15k - 5k = 10k/mo x 6 = 60k
        // Period 2: rent 18k - 5k = 13k/mo x 6 = 78k
        // Total = 1,38,000
        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.of("138000"));
    }

    @Test
    @DisplayName("zero HRA received yields zero exemption")
    void zeroHraReceivedYieldsZero() {
        List<HraMonthSalary> salaries = create12MonthsSalary(Money.of("50000"), Money.ZERO);
        List<RentPeriod> rentPeriods =
                List.of(new RentPeriod(YearMonth.of(2025, 4), YearMonth.of(2026, 3), Money.of("18000"), true));

        HraExemptionResult result = HraExemption.calculate(true, rule, salaries, rentPeriods);

        assertThat(result.totalExemption()).isEqualTo(Money.ZERO);
    }
}
