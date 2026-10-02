package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link EmployeeTdsRules} (W-36.1 §7).
 */
class EmployeeTdsRulesTest {

    private final FinancialYear fy2026 = FinancialYear.of(2026, 2027);

    @ParameterizedTest
    @ValueSource(strings = {"2026", "2026-2026", "2026-2028", "26-27", "invalid", "", "   "})
    @DisplayName("Bad FY label is rejected with 400 validation error")
    void badFinancialYearLabelRejected(String badLabel) {
        assertThatThrownBy(() -> EmployeeTdsRules.validateFinancialYear(badLabel))
                .isInstanceOf(EmployeeTdsValidationException.class);
    }

    @Test
    @DisplayName("Valid consecutive FY label parses correctly")
    void validFinancialYearParses() {
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear("2026-2027");
        assertThat(fy.startYear()).isEqualTo(2026);
        assertThat(fy.endYear()).isEqualTo(2027);
        assertThat(fy.label()).isEqualTo("2026-2027");
    }

    @Test
    @DisplayName("annual_taxable_income > annual_gross is rejected")
    void taxableIncomeExceedingGrossRejected() {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("600000.00"), // > gross
                new BigDecimal("50000.00"),
                "2026-04",
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figures, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("annual_taxable_income");
    }

    @Test
    @DisplayName("Negative tax is rejected")
    void negativeTaxRejected() {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("600000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("-100.00"), // negative
                "2026-04",
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figures, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("annual_tax must be non-negative");
    }

    @Test
    @DisplayName("effective_from_period outside FY is rejected")
    void effectiveFromPeriodOutsideFyRejected() {
        // FY 2026-2027 is 2026-04 to 2027-03. 2026-03 is prior FY.
        TdsFigures figuresPrior = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("600000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("50000.00"),
                "2026-03", // outside FY
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figuresPrior, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("outside financial year");

        // 2027-04 is next FY.
        TdsFigures figuresNext = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("600000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("50000.00"),
                "2027-04", // outside FY
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figuresNext, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("outside financial year");
    }

    @Test
    @DisplayName("Regime null is rejected")
    void nullRegimeRejected() {
        TdsFigures figures = new TdsFigures(
                null,
                new BigDecimal("600000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("50000.00"),
                "2026-04",
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figures, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("regime is required");
    }

    @Test
    @DisplayName("Scale greater than 2 is rejected")
    void scaleGreaterThanTwoRejected() {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("600000.005"), // scale 3
                new BigDecimal("500000.00"),
                new BigDecimal("50000.00"),
                "2026-04",
                null,
                "note");

        assertThatThrownBy(() -> EmployeeTdsRules.validateFigures(figures, fy2026))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("scale must be at most 2");
    }
}
