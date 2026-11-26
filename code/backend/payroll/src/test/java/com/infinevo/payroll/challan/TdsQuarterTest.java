package com.infinevo.payroll.challan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The quarter rules of the quarterly TDS return (W-89): Apr–Jun Q1 … Jan–Mar Q4, by the day TDS was deducted. */
class TdsQuarterTest {

    private static final FinancialYear FY_26 = FinancialYear.of(2026);

    @ParameterizedTest(name = "{0} is {1} {2}")
    @CsvSource({
        "2026-04-01, 2026-2027, Q1",
        "2026-06-30, 2026-2027, Q1",
        "2026-07-01, 2026-2027, Q2",
        "2026-09-30, 2026-2027, Q2",
        "2026-10-01, 2026-2027, Q3",
        "2026-12-31, 2026-2027, Q3",
        "2027-01-01, 2026-2027, Q4",
        "2027-03-31, 2026-2027, Q4",
        "2027-04-01, 2027-2028, Q1"
    })
    void quarterOfADate(LocalDate date, String fy, String quarter) {
        TdsQuarter q = TdsQuarter.of(date);
        assertThat(q.financialYear().label()).isEqualTo(fy);
        assertThat(q.quarter()).isEqualTo(quarter);
    }

    @Test
    @DisplayName("Q4 is January to March of the year's end year")
    void q4MonthsAreInTheEndYear() {
        TdsQuarter q4 = new TdsQuarter(FY_26, "Q4");
        assertThat(q4.months()).containsExactly(YearMonth.of(2027, 1), YearMonth.of(2027, 2), YearMonth.of(2027, 3));
        assertThat(q4.start()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(q4.end()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(q4.isQ4()).isTrue();
    }

    @Test
    void eachQuarterHasItsThreeMonths() {
        assertThat(new TdsQuarter(FY_26, "Q1").months())
                .containsExactly(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
        assertThat(new TdsQuarter(FY_26, "Q2").months())
                .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        assertThat(new TdsQuarter(FY_26, "Q3").months())
                .containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12));
    }

    @Test
    @DisplayName("Deduction date is the day paid, before the pay date and the month end")
    void deductionDatePrefersPaidOn() {
        PayRun run = run(YearMonth.of(2026, 5), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 5, 30));
        assertThat(TdsQuarter.deductionDate(run)).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void deductionDateFallsBackToPayDate() {
        PayRun run = run(YearMonth.of(2026, 5), null, LocalDate.of(2026, 5, 30));
        assertThat(TdsQuarter.deductionDate(run)).isEqualTo(LocalDate.of(2026, 5, 30));
    }

    @Test
    void deductionDateFallsBackToPeriodEnd() {
        PayRun run = run(YearMonth.of(2026, 2), null, null);
        assertThat(TdsQuarter.deductionDate(run)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("A March salary paid on 2 April is in Q1 of the next year")
    void marchSalaryPaidInAprilIsNextYearQ1() {
        PayRun run = run(YearMonth.of(2027, 3), LocalDate.of(2027, 4, 2), LocalDate.of(2027, 3, 31));
        TdsQuarter q = TdsQuarter.of(run);
        assertThat(q).isEqualTo(new TdsQuarter(FinancialYear.of(2027), "Q1"));
    }

    @Test
    @DisplayName("A June salary paid on 30 June stays in Q1")
    void paidInsideTheQuarterStays() {
        PayRun run = run(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 30), null);
        assertThat(TdsQuarter.of(run)).isEqualTo(new TdsQuarter(FY_26, "Q1"));
    }

    @Test
    @DisplayName("Candidate months are the month before the quarter and its three months")
    void candidateMonths() {
        assertThat(new TdsQuarter(FY_26, "Q1").candidateMonths())
                .containsExactly(
                        YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
        assertThat(new TdsQuarter(FY_26, "Q4").candidateMonths())
                .containsExactly(
                        YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2), YearMonth.of(2027, 3));
    }

    @Test
    void parseAcceptsLowerCaseAndSpaces() {
        TdsQuarter q = TdsQuarter.parse(" 2026-2027 ", " q3 ");
        assertThat(q).isEqualTo(new TdsQuarter(FY_26, "Q3"));
        assertThat(q.label()).isEqualTo("2026-2027 Q3");
    }

    @Test
    void parseRejectsBadInput() {
        assertThatThrownBy(() -> TdsQuarter.parse("2026-2027", "Q5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TdsQuarter.parse("2026-27", "Q1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TdsQuarter.parse(null, "Q1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TdsQuarter.parse("2026-2027", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TdsQuarter(FY_26, "q1")).isInstanceOf(IllegalArgumentException.class);
    }

    private static PayRun run(YearMonth period, LocalDate paidOn, LocalDate payDate) {
        PayRun run = mock(PayRun.class);
        when(run.getPeriod()).thenReturn(period);
        when(run.getPaidOn()).thenReturn(paidOn);
        when(run.getPayDate()).thenReturn(payDate);
        return run;
    }
}
