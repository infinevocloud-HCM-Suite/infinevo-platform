package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaxSummaryFiguresMapperTest {

    private final FinancialYear fy2025_2026 = FinancialYear.of(2025, 2026);

    private TaxComputation sampleComputation() {
        return new TaxComputation(
                TaxRegime.NEW,
                fy2025_2026.label(),
                Money.of(1575000),
                Money.ZERO,
                Money.of(75000),
                Money.of(1575000),
                Money.of(1500000),
                Money.of(105000),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.of(4200),
                Money.ZERO,
                Money.of(109200),
                List.of(),
                List.of("assumed 12 months"));
    }

    @Test
    @DisplayName("Maps all 11 tax figures correctly according to spec ? 4")
    void mapsTaxFiguresCorrectly() {
        // Given current time in mid-year: 2025-09-15
        Clock clock = Clock.fixed(Instant.parse("2025-09-15T10:00:00Z"), ZoneOffset.UTC);
        TaxSummaryFiguresMapper mapper = new TaxSummaryFiguresMapper(clock);

        TaxComputation comp = sampleComputation();
        TaxSummaryFigures figures = mapper.toFigures(comp, fy2025_2026);

        // taxable_income = grossTotalIncome
        assertThat(figures.taxableIncome()).isEqualByComparingTo(new BigDecimal("1575000"));
        // net_taxable_income = taxableIncome
        assertThat(figures.netTaxableIncome()).isEqualByComparingTo(new BigDecimal("1500000"));
        // tax_on_taxable_income = taxBeforeRebate - rebate + surcharge + cess
        assertThat(figures.taxOnTaxableIncome()).isEqualByComparingTo(new BigDecimal("109200"));
        // tax_to_be_paid = annualTax
        assertThat(figures.taxToBePaid()).isEqualByComparingTo(new BigDecimal("109200"));
        // tds_previous_employer = prevEmploymentTds
        assertThat(figures.tdsPreviousEmployer()).isEqualByComparingTo(BigDecimal.ZERO);
        // Placeholders until W-36.1
        assertThat(figures.taxYtdAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(figures.tdsThroughPayroll()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(figures.tdsOtherIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(figures.otherSourcesIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(figures.exemptionUnderSection10()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(figures.exemptionUnderSection6a()).isEqualByComparingTo(BigDecimal.ZERO);
        // September to March = 7 months (Sep, Oct, Nov, Dec, Jan, Feb, Mar)
        assertThat(figures.remainingMonths()).isEqualTo(7);
    }

    @Test
    @DisplayName("Calculates remaining months correctly across dates")
    void calculatesRemainingMonths() {
        // Before FY starts -> 12 months
        TaxSummaryFiguresMapper mapperBefore =
                new TaxSummaryFiguresMapper(Clock.fixed(Instant.parse("2025-02-15T00:00:00Z"), ZoneOffset.UTC));
        assertThat(mapperBefore.calculateRemainingMonths(fy2025_2026)).isEqualTo(12);

        // First month of FY (April) -> 12 months
        TaxSummaryFiguresMapper mapperApril =
                new TaxSummaryFiguresMapper(Clock.fixed(Instant.parse("2025-04-10T00:00:00Z"), ZoneOffset.UTC));
        assertThat(mapperApril.calculateRemainingMonths(fy2025_2026)).isEqualTo(12);

        // Last month of FY (March) -> 1 month
        TaxSummaryFiguresMapper mapperMarch =
                new TaxSummaryFiguresMapper(Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(mapperMarch.calculateRemainingMonths(fy2025_2026)).isEqualTo(1);

        // After FY ends -> 0 months
        TaxSummaryFiguresMapper mapperAfter =
                new TaxSummaryFiguresMapper(Clock.fixed(Instant.parse("2026-04-05T00:00:00Z"), ZoneOffset.UTC));
        assertThat(mapperAfter.calculateRemainingMonths(fy2025_2026)).isEqualTo(0);
    }
}
