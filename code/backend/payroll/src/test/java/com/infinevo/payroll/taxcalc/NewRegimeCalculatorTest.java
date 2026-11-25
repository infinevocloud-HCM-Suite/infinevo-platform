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
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncome;
import com.infinevo.payroll.taxdeclaration.summary.OtherIncomeKind;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NewRegimeCalculator} covering the hand calculations and edge cases
 * from W-33.1 spec § 3 and § 7.
 */
class NewRegimeCalculatorTest {

    private static final FinancialYear FY_2025_2026 = FinancialYear.parse("2025-2026");
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID DECLARATION_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

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

    /*
     * Surcharge hand calculations, FY 2025-26 NEW slabs as stubbed in setUp():
     *   tax at 24,00,000 = 0 + 20,000 + 40,000 + 60,000 + 80,000 + 1,00,000 = 3,00,000; 30 % above.
     *   tax at the 50,00,000 threshold = 3,00,000 + 30 % x 26,00,000 = 10,80,000; no surcharge at
     *   the threshold itself (no band ends at 50,00,000), so tax + surcharge at threshold = 10,80,000.
     */

    @Test
    @DisplayName("taxable 50,10,000: full 10% surcharge, no marginal relief (W-33.3) -> annual tax 12,38,952")
    void fullSurchargeJustAboveFiftyLakh() {
        // salary 50,85,000 - SD 75,000 = taxable 50,10,000
        // slab tax       = 3,00,000 + 30% x 26,10,000          = 10,83,000 (no 87A rebate)
        // nominal surch. = 10% x 10,83,000                     =  1,08,300
        // surcharge      = 1,08,300, no marginal relief (W-33.3, as legacy)
        // cess           = 4% x (10,83,000 + 1,08,300)         =    47,652
        // annual tax     = 10,83,000 + 1,08,300 + 47,652       = 12,38,952
        TaxInput input = createInput(Money.of("5085000"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("5010000"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("1083000"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        assertThat(computation.surcharge()).isEqualTo(Money.of("108300"));
        assertThat(computation.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(computation.cess()).isEqualTo(Money.of("47652"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("1238952"));
    }

    @Test
    @DisplayName("taxable 55,00,000: full 10% surcharge, no marginal relief -> annual tax 14,07,120")
    void surchargeClearOfMarginalRelief() {
        // salary 55,75,000 - SD 75,000 = taxable 55,00,000
        // slab tax       = 3,00,000 + 30% x 31,00,000          = 12,30,000
        // nominal surch. = 10% x 12,30,000                     =  1,23,000
        // cess           = 4% x (12,30,000 + 1,23,000)         =    54,120
        // annual tax     = 12,30,000 + 1,23,000 + 54,120       = 14,07,120
        TaxInput input = createInput(Money.of("5575000"), Map.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("5500000"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("1230000"));
        assertThat(computation.surcharge()).isEqualTo(Money.of("123000"));
        assertThat(computation.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(computation.cess()).isEqualTo(Money.of("54120"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("1407120"));
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

    private TaxInput createInputWithDeclaredIncome(
            Money annualSalary,
            List<EmployeeInvOtherIncome> otherIncome,
            boolean hasLetOutProperty,
            List<EmployeeInvLetOutProperty> letOutProperties) {
        SalaryProjectionResult salaryResult =
                new SalaryProjectionResult(annualSalary, List.of(), List.of("Assumption"));
        return new TaxInput(
                EMPLOYEE_ID,
                DECLARATION_ID,
                salaryResult,
                Map.of(),
                AgeCategory.GENERAL,
                false,
                false,
                hasLetOutProperty,
                List.of(),
                List.of(),
                letOutProperties,
                List.of(),
                Map.of(),
                otherIncome,
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static EmployeeInvOtherIncome otherIncome(OtherIncomeKind kind, String amount) {
        return new EmployeeInvOtherIncome(TENANT_ID, DECLARATION_ID, kind, kind.name(), new BigDecimal(amount));
    }

    private static EmployeeInvLetOutProperty letOut(String netIncomeLoss) {
        return new EmployeeInvLetOutProperty(TENANT_ID, DECLARATION_ID, "Flat", "Pune", new BigDecimal(netIncomeLoss));
    }

    @Test
    @DisplayName(
            "D-148: declared interest counted, no 80TTA: salary 15,00,000 + interest 75,000 -> annual tax 1,09,200")
    void otherIncomeOnly() {
        // GTI = 15,00,000 + 50,000 + 25,000 = 15,75,000; taxable = 15,75,000 - 75,000 = 15,00,000
        // slab tax 1,05,000, no rebate, cess 4,200 -> 1,09,200 (savings interest gets no 80TTA here)
        TaxInput input = createInputWithDeclaredIncome(
                Money.of("1500000"),
                List.of(
                        otherIncome(OtherIncomeKind.SAVINGS_INTEREST, "50000"),
                        otherIncome(OtherIncomeKind.FD_INTEREST, "25000")),
                false,
                List.of());

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.incomeFromSalary()).isEqualTo(Money.of("1500000"));
        assertThat(computation.otherIncome()).isEqualTo(Money.of("75000"));
        assertThat(computation.interestDeduction()).isEqualTo(Money.ZERO);
        assertThat(computation.chapterViaDeductions()).isEqualTo(Money.ZERO);
        assertThat(computation.houseProperty().housePropertyIncome()).isEqualTo(Money.ZERO);
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("1575000"));
        assertThat(computation.standardDeduction()).isEqualTo(Money.of("75000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1500000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
    }

    @Test
    @DisplayName("D-148: net let-out income counted: salary 13,75,000 + let-out 2,00,000 -> annual tax 1,09,200")
    void letOutIncomeOnly() {
        TaxInput input = createInputWithDeclaredIncome(
                Money.of("1375000"), List.of(), true, List.of(letOut("120000"), letOut("80000")));

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.houseProperty().letOutNet()).isEqualTo(Money.of("200000"));
        assertThat(computation.houseProperty().housePropertyIncome()).isEqualTo(Money.of("200000"));
        assertThat(computation.houseProperty().selfOccupiedInterest()).isEqualTo(Money.ZERO);
        assertThat(computation.houseProperty().lossCapApplied()).isFalse();
        assertThat(computation.otherIncome()).isEqualTo(Money.ZERO);
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("1575000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1500000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
    }

    @Test
    @DisplayName("D-148: other income and let-out income push salary 12,75,000 past the 87A threshold -> 1,09,200")
    void otherIncomeAndLetOutIncome() {
        // Salary alone: taxable 12,00,000 -> full 87A rebate -> 0. With 1,00,000 interest and
        // 2,00,000 let-out: GTI 15,75,000 -> taxable 15,00,000 -> 1,09,200.
        TaxInput input = createInputWithDeclaredIncome(
                Money.of("1275000"),
                List.of(otherIncome(OtherIncomeKind.NSC_INTEREST, "100000")),
                true,
                List.of(letOut("200000")));

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.otherIncome()).isEqualTo(Money.of("100000"));
        assertThat(computation.houseProperty().housePropertyIncome()).isEqualTo(Money.of("200000"));
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("1575000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1500000"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
    }

    @Test
    @DisplayName("D-148: a net let-out loss is not set off against salary under the new regime")
    void letOutLossNotSetOff() {
        TaxInput input = createInputWithDeclaredIncome(
                Money.of("1575000"), List.of(), true, List.of(letOut("50000"), letOut("-200000")));

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.houseProperty().letOutNet()).isEqualTo(Money.of("-150000"));
        assertThat(computation.houseProperty().housePropertyIncome()).isEqualTo(Money.ZERO);
        assertThat(computation.houseProperty().lossCapApplied()).isTrue();
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("1575000"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("1500000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
        assertThat(computation.assumptions()).anyMatch(a -> a.contains("115BAC"));
    }

    @Test
    @DisplayName("D-148: let-out rows are ignored when the declaration says there is no let-out property")
    void letOutRowsIgnoredWithoutFlag() {
        TaxInput input =
                createInputWithDeclaredIncome(Money.of("1575000"), List.of(), false, List.of(letOut("200000")));

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.houseProperty().housePropertyIncome()).isEqualTo(Money.ZERO);
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("1575000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("109200"));
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
