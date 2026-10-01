package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NewRegimeCalculator} covering the hand calculations and edge cases
 * from W-33.1 spec ? 3 and ? 7.
 */
class NewRegimeCalculatorTest {

    private static final FinancialYear FY_2025_2026 = FinancialYear.parse("2025-2026");
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID DECLARATION_ID = UUID.randomUUID();

    private TaxRuleReader ruleReader;
    private NewRegimeCalculator calculator;

    @BeforeEach
    void setUp() {
        ruleReader = mock(TaxRuleReader.class);
        calculator = new NewRegimeCalculator(ruleReader);

        // Standard statutory rules for FY 2025-2026 NEW
        given(ruleReader.standardDeduction(eq(FY_2025_2026), eq(TaxRegime.NEW)))
                .willReturn(new StandardDeductionRule("2025-2026", "NEW", Money.of("75000"), "Standard deduction"));

        given(ruleReader.rebate(eq(FY_2025_2026), eq(TaxRegime.NEW)))
                .willReturn(new Section87aRebateRule(
                        "2025-2026", "NEW", Money.of("1200000"), Money.of("60000"), true, "Rebate"));

        given(ruleReader.cess(eq(FY_2025_2026), eq(TaxRegime.NEW)))
                .willReturn(new CessSurchargeRule(
                        "2025-2026", "CESS", "BOTH", null, null, BigDecimal.valueOf(4.00), false, "Cess"));

        given(ruleReader.surchargeBands(eq(FY_2025_2026), eq(TaxRegime.NEW)))
                .willReturn(List.of(
                        new CessSurchargeRule(
                                "2025-2026",
                                "SURCHARGE",
                                "BOTH",
                                Money.of("5000000"),
                                Money.of("10000000"),
                                BigDecimal.valueOf(10.00),
                                true,
                                "50L-1Cr"),
                        new CessSurchargeRule(
                                "2025-2026",
                                "SURCHARGE",
                                "NEW",
                                Money.of("10000000"),
                                null,
                                BigDecimal.valueOf(15.00),
                                true,
                                "1Cr+")));

        // 7 Slabs for 2025-2026 NEW
        List<TaxSlabDetail> slabs = List.of(
                new TaxSlabDetail(Money.of("0"), Money.of("400000"), BigDecimal.valueOf(0), 1),
                new TaxSlabDetail(Money.of("400000"), Money.of("800000"), BigDecimal.valueOf(5), 2),
                new TaxSlabDetail(Money.of("800000"), Money.of("1200000"), BigDecimal.valueOf(10), 3),
                new TaxSlabDetail(Money.of("1200000"), Money.of("1600000"), BigDecimal.valueOf(15), 4),
                new TaxSlabDetail(Money.of("1600000"), Money.of("2000000"), BigDecimal.valueOf(20), 5),
                new TaxSlabDetail(Money.of("2000000"), Money.of("2400000"), BigDecimal.valueOf(25), 6),
                new TaxSlabDetail(Money.of("2400000"), null, BigDecimal.valueOf(30), 7));

        given(ruleReader.slabs(eq(FY_2025_2026), eq(TaxRegime.NEW), any())).willReturn(slabs);
    }

    private TaxInput createInput(Money annualSalary, Map<PrevEmploymentKind, Money> prevEmployment) {
        SalaryProjectionResult salaryResult =
                new SalaryProjectionResult(annualSalary, List.of(), List.of("Assumption"));
        return new TaxInput(EMPLOYEE_ID, DECLARATION_ID, salaryResult, prevEmployment, AgeCategory.GENERAL);
    }

    @Test
    @DisplayName("the hand calculation: salary 15,75,000, no previous employer -> annual tax 1,09,200")
    void handCalculationScenario1() {
        TaxInput input = createInput(Money.of("1575000"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.standardDeduction()).isEqualTo(Money.of("75000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1500000"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("105000"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        assertThat(computation.surcharge()).isEqualTo(Money.ZERO);
        assertThat(computation.cess()).isEqualTo(Money.of("4200"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
    }

    @Test
    @DisplayName("salary 12,75,000 -> taxable 12,00,000, tax 60,000, full rebate -> annual tax 0")
    void fullRebateScenario2() {
        TaxInput input = createInput(Money.of("1275000"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1200000"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("60000"));
        assertThat(computation.rebate()).isEqualTo(Money.of("60000"));
        assertThat(computation.annualTax()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("salary 12,75,001 -> taxable 12,00,001 -> rebate 0 (threshold is strictly on taxable income)")
    void thresholdExceededScenario3() {
        TaxInput input = createInput(Money.of("1275001"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1200001"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        // Tax is computed and positive
        assertThat(computation.annualTax()).isGreaterThan(Money.of("60000"));
    }

    @Test
    @DisplayName("previous-employer income 2,00,000 and TDS 10,000 added and subtracted")
    void previousEmployerIncomeAndTdsScenario4() {
        TaxInput input = createInput(
                Money.of("1375000"),
                Map.of(
                        PrevEmploymentKind.INCOME, Money.of("200000"),
                        PrevEmploymentKind.INCOME_TAX_DEDUCTED, Money.of("10000")));

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        // 13.75L + 2L = 15.75L gross -> 15.00L taxable -> 1,09,200 before TDS
        // Annual tax = 1,09,200 - 10,000 = 99,200
        assertThat(computation.incomeFromSalary()).isEqualTo(Money.of("1575000"));
        assertThat(computation.prevEmploymentIncome()).isEqualTo(Money.of("200000"));
        assertThat(computation.prevEmploymentTds()).isEqualTo(Money.of("10000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("99200"));
    }

    @Test
    @DisplayName("standard deduction never exceeds income")
    void standardDeductionNeverExceedsIncome() {
        TaxInput input = createInput(Money.of("50000"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.standardDeduction()).isEqualTo(Money.of("50000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.ZERO);
        assertThat(computation.annualTax()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("RegimeCalculators registry returns NEW and throws 409 for OLD")
    void regimeCalculatorsRegistryBehavior() {
        RegimeCalculators registry = new RegimeCalculators(List.of(calculator));

        assertThat(registry.forRegime(TaxRegime.NEW)).isSameAs(calculator);
        assertThat(registry.available()).containsExactly(calculator);

        assertThatThrownBy(() -> registry.forRegime(TaxRegime.OLD))
                .isInstanceOf(RegimeNotAvailableException.class)
                .hasMessageContaining("OLD");
    }
}
