package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.DeclaredItem;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxcalc.model.MonthProjection;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxcalc.reader.model.HraRule;
import com.infinevo.payroll.taxcalc.reader.model.LetOutRule;
import com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader.Section6AItem;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OldRegimeCalculator} validating statutory calculations and hand calculation cases (W-33.2 spec § 7).
 */
class OldRegimeCalculatorTest {

    private static final FinancialYear FY_2025_2026 = FinancialYear.parse("2025-2026");
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID DECLARATION_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private TaxRuleReader ruleReader;
    private Section6AItemReader section6AItemReader;
    private OldRegimeCalculator calculator;

    @BeforeEach
    void setUp() {
        ruleReader = mock(TaxRuleReader.class);
        section6AItemReader = mock(Section6AItemReader.class);
        calculator = new OldRegimeCalculator(ruleReader, section6AItemReader);

        // Standard statutory rules
        HraRule hraRule = new HraRule(
                "2025-2026", BigDecimal.valueOf(10.00), BigDecimal.valueOf(50.00), BigDecimal.valueOf(40.00));
        StandardDeductionRule stdRule =
                new StandardDeductionRule("2025-2026", "OLD", Money.of("50000"), "Standard deduction");
        HomeLoanRule rule24B = new HomeLoanRule(
                "2025-2026",
                "24B",
                "Interest on borrowed capital",
                "INTEREST",
                "SELF_OCCUPIED",
                Money.of("200000"),
                null,
                null,
                false);
        LetOutRule letOutRule =
                new LetOutRule("2025-2026", "OLD", BigDecimal.valueOf(30.00), Money.of("200000"), true, true);
        OtherIncomeRule rule80Tta = new OtherIncomeRule(
                "2025-2026",
                "80TTA",
                "Interest on savings",
                "DEDUCTION",
                "OLD",
                Money.of("10000"),
                BigDecimal.valueOf(100),
                false,
                false);
        OtherIncomeRule rule80Ttb = new OtherIncomeRule(
                "2025-2026",
                "80TTB",
                "Interest income, senior citizen",
                "DEDUCTION",
                "OLD",
                Money.of("50000"),
                BigDecimal.valueOf(100),
                false,
                true);
        Section87aRebateRule rebateRule = new Section87aRebateRule(
                "2025-2026", "OLD", Money.of("500000"), Money.of("12500"), false, "Section 87A rebate");
        CessSurchargeRule cessRule =
                new CessSurchargeRule("2025-2026", "CESS", "BOTH", null, null, BigDecimal.valueOf(4.00), false, "Cess");

        given(ruleReader.hra(FY_2025_2026)).willReturn(hraRule);
        given(ruleReader.standardDeduction(FY_2025_2026, TaxRegime.OLD)).willReturn(stdRule);
        given(ruleReader.homeLoan(FY_2025_2026, "24B", "INTEREST", "SELF_OCCUPIED"))
                .willReturn(rule24B);
        given(ruleReader.letOut(FY_2025_2026)).willReturn(letOutRule);
        given(ruleReader.otherIncomeRule(FY_2025_2026, "80TTA")).willReturn(rule80Tta);
        given(ruleReader.otherIncomeRule(FY_2025_2026, "80TTB")).willReturn(rule80Ttb);
        given(ruleReader.rebate(FY_2025_2026, TaxRegime.OLD)).willReturn(rebateRule);
        given(ruleReader.cess(FY_2025_2026, TaxRegime.OLD)).willReturn(cessRule);
        given(ruleReader.surchargeBands(FY_2025_2026, TaxRegime.OLD)).willReturn(List.of());
        given(ruleReader.homeLoanRules(FY_2025_2026)).willReturn(List.of(rule24B));

        given(section6AItemReader.groupCap("80C_GROUP")).willReturn(new BigDecimal("150000"));
        given(section6AItemReader.activeItems("OLD"))
                .willReturn(List.of(new Section6AItem(
                        UUID.randomUUID(),
                        "80CCD(1B)",
                        "INVESTMENT",
                        "NPS additional",
                        "",
                        new BigDecimal("50000"),
                        null,
                        false,
                        false,
                        true,
                        false,
                        4,
                        true)));
    }

    private List<TaxSlabDetail> generalOldSlabs() {
        return List.of(
                new TaxSlabDetail(Money.of("0"), Money.of("250000"), BigDecimal.ZERO, 1),
                new TaxSlabDetail(Money.of("250000"), Money.of("500000"), BigDecimal.valueOf(5), 2),
                new TaxSlabDetail(Money.of("500000"), Money.of("1000000"), BigDecimal.valueOf(20), 3),
                new TaxSlabDetail(Money.of("1000000"), null, BigDecimal.valueOf(30), 4));
    }

    private List<TaxSlabDetail> seniorOldSlabs() {
        return List.of(
                new TaxSlabDetail(Money.of("0"), Money.of("300000"), BigDecimal.ZERO, 1),
                new TaxSlabDetail(Money.of("300000"), Money.of("500000"), BigDecimal.valueOf(5), 2),
                new TaxSlabDetail(Money.of("500000"), Money.of("1000000"), BigDecimal.valueOf(20), 3),
                new TaxSlabDetail(Money.of("1000000"), null, BigDecimal.valueOf(30), 4));
    }

    @Test
    @DisplayName(
            "General employee hand calculation: salary 12L, rent 20k/mo metro, 80C 1.5L, 80D 25k, PT 2,400 -> taxable 7,92,600, annual tax 73,861")
    void handCalculationGeneral() {
        given(ruleReader.slabs(FY_2025_2026, TaxRegime.OLD, AgeCategory.GENERAL))
                .willReturn(generalOldSlabs());

        List<MonthProjection> monthList = new ArrayList<>();
        List<HraMonthSalary> hraSalaries = new ArrayList<>();
        YearMonth ym = YearMonth.of(2025, 4);
        for (int i = 0; i < 12; i++) {
            monthList.add(new MonthProjection(ym, Money.of("100000"), null, ""));
            hraSalaries.add(new HraMonthSalary(ym, Money.of("50000"), Money.of("20000")));
            ym = ym.plusMonths(1);
        }
        SalaryProjectionResult salaryResult = new SalaryProjectionResult(Money.of("1200000"), monthList, List.of());

        EmployeeInvHouseRent rent = new EmployeeInvHouseRent(
                TENANT_ID,
                DECLARATION_ID,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2026, 3, 31),
                "Mumbai",
                "Landlord",
                "ABCDE1234F",
                true, // metro
                new BigDecimal("20000"));

        DeclaredItem sec80c = new DeclaredItem("80C", "PPF", "80C_GROUP", Money.of("150000"), Money.of("150000"));
        DeclaredItem sec80d = new DeclaredItem("80D", "Health insurance", null, Money.of("25000"), Money.of("100000"));

        TaxInput input = new TaxInput(
                EMPLOYEE_ID,
                DECLARATION_ID,
                salaryResult,
                Map.of(),
                AgeCategory.GENERAL,
                true, // isStayingInRentedHouse
                false,
                false,
                List.of(rent),
                List.of(),
                List.of(),
                List.of(sec80c, sec80d),
                Map.of(PreTaxDeductionKind.PROFESSIONAL_TAX, Money.of("2400")),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                hraSalaries);

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.hraExemption()).isEqualTo(Money.of("180000"));
        assertThat(computation.standardDeduction()).isEqualTo(Money.of("50000"));
        assertThat(computation.professionalTax()).isEqualTo(Money.of("2400"));
        assertThat(computation.grossTotalIncome()).isEqualTo(Money.of("967600"));
        assertThat(computation.taxableIncome()).isEqualTo(Money.of("792600"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("71020"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        assertThat(computation.cess()).isEqualTo(Money.of("2840.8000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("73861"));
    }

    @Test
    @DisplayName("Senior citizen (60-79) hand calculation with higher basic exemption: annual tax 71,261")
    void handCalculationSenior() {
        given(ruleReader.slabs(FY_2025_2026, TaxRegime.OLD, AgeCategory.SENIOR)).willReturn(seniorOldSlabs());

        List<MonthProjection> monthList = new ArrayList<>();
        List<HraMonthSalary> hraSalaries = new ArrayList<>();
        YearMonth ym = YearMonth.of(2025, 4);
        for (int i = 0; i < 12; i++) {
            monthList.add(new MonthProjection(ym, Money.of("100000"), null, ""));
            hraSalaries.add(new HraMonthSalary(ym, Money.of("50000"), Money.of("20000")));
            ym = ym.plusMonths(1);
        }
        SalaryProjectionResult salaryResult = new SalaryProjectionResult(Money.of("1200000"), monthList, List.of());

        EmployeeInvHouseRent rent = new EmployeeInvHouseRent(
                TENANT_ID,
                DECLARATION_ID,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2026, 3, 31),
                "Mumbai",
                "Landlord",
                "ABCDE1234F",
                true,
                new BigDecimal("20000"));

        DeclaredItem sec80c = new DeclaredItem("80C", "PPF", "80C_GROUP", Money.of("150000"), Money.of("150000"));
        DeclaredItem sec80d = new DeclaredItem("80D", "Health insurance", null, Money.of("25000"), Money.of("100000"));

        TaxInput input = new TaxInput(
                EMPLOYEE_ID,
                DECLARATION_ID,
                salaryResult,
                Map.of(),
                AgeCategory.SENIOR,
                true,
                false,
                false,
                List.of(rent),
                List.of(),
                List.of(),
                List.of(sec80c, sec80d),
                Map.of(PreTaxDeductionKind.PROFESSIONAL_TAX, Money.of("2400")),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                hraSalaries);

        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("792600"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("68520"));
        assertThat(computation.rebate()).isEqualTo(Money.ZERO);
        assertThat(computation.cess()).isEqualTo(Money.of("2740.8000"));
        assertThat(computation.annualTax()).isEqualTo(Money.of("71261"));
    }

    @Test
    @DisplayName("Low income employee (taxable 4.8L): slab tax 11,500, rebate 11,500 -> annual tax 0")
    void lowIncomeRebate87AFullRelief() {
        given(ruleReader.slabs(FY_2025_2026, TaxRegime.OLD, AgeCategory.GENERAL))
                .willReturn(generalOldSlabs());

        SalaryProjectionResult salaryResult = new SalaryProjectionResult(Money.of("530000"), List.of(), List.of());

        TaxInput input = new TaxInput(
                EMPLOYEE_ID,
                DECLARATION_ID,
                salaryResult,
                Map.of(),
                AgeCategory.GENERAL,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of());

        // Salary 5,30,000 - std ded 50,000 = taxable 4,80,000
        TaxComputation computation = calculator.compute(input, FY_2025_2026);

        assertThat(computation.taxableIncome()).isEqualTo(Money.of("480000"));
        assertThat(computation.taxBeforeRebate()).isEqualTo(Money.of("11500"));
        assertThat(computation.rebate()).isEqualTo(Money.of("11500"));
        assertThat(computation.annualTax()).isEqualTo(Money.ZERO);
    }
}
