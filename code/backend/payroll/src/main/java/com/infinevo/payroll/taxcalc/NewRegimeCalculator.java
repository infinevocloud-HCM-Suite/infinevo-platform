package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.engine.Rebate87A;
import com.infinevo.payroll.taxcalc.engine.SlabTax;
import com.infinevo.payroll.taxcalc.engine.SurchargeAndCess;
import com.infinevo.payroll.taxcalc.model.SlabTaxResult;
import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Income tax calculator for the New Tax Regime (W-33.1 spec ? 3, ? 4).
 *
 * <p>Implements the eight-step statutory pipeline:
 * <ol>
 *   <li>Income from salary = projected salary + previous employer income</li>
 *   <li>Standard deduction = min(rule.amount, incomeFromSalary)</li>
 *   <li>Taxable income = max(0, incomeFromSalary ? standardDeduction), rounded to rupee</li>
 *   <li>Tax before rebate = progressive slab tax across 7 brackets</li>
 *   <li>Section 87A rebate = full rebate up to threshold, else zero</li>
 *   <li>High-income surcharge with statutory marginal relief</li>
 *   <li>Health & education cess (4%) on tax plus surcharge</li>
 *   <li>Annual tax = max(0, tax + surcharge + cess ? previous employer TDS), rounded to rupee</li>
 * </ol>
 */
@Component
public class NewRegimeCalculator implements RegimeCalculator {

    private final TaxRuleReader ruleReader;

    public NewRegimeCalculator(TaxRuleReader ruleReader) {
        this.ruleReader = Objects.requireNonNull(ruleReader, "ruleReader must not be null");
    }

    @Override
    public TaxRegime regime() {
        return TaxRegime.NEW;
    }

    @Override
    public TaxComputation compute(TaxInput input, FinancialYear fy) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(fy, "fy must not be null");

        // Step 1: Gross salary + previous employment income
        Money salary = input.salary().annualTaxableSalary();
        Money prevIncome = input.prevEmploymentIncome();
        Money incomeFromSalary = salary.add(prevIncome);
        Money grossTotalIncome = incomeFromSalary;

        // Step 2: Standard deduction from reference.standard_deduction_rule_master
        StandardDeductionRule stdRule = ruleReader.standardDeduction(fy, TaxRegime.NEW);
        Money standardDeduction =
                stdRule.amount().compareTo(incomeFromSalary) < 0 ? stdRule.amount() : incomeFromSalary;

        // Step 3: Taxable income rounded to the rupee (Money scale 4 raw rounded half-up)
        Money taxableRaw = incomeFromSalary.subtract(standardDeduction);
        if (taxableRaw.isNegative()) {
            taxableRaw = Money.ZERO;
        }
        Money taxableIncome = Money.of(taxableRaw.raw().setScale(0, RoundingMode.HALF_UP));

        // Step 4: Slabs computation
        List<TaxSlabDetail> slabs = ruleReader.slabs(fy, TaxRegime.NEW, input.ageCategory());
        SlabTaxResult slabResult = SlabTax.of(taxableIncome, slabs);
        Money taxBeforeRebate = slabResult.totalTax();

        // Step 5: Section 87A rebate
        Section87aRebateRule rebateRule = ruleReader.rebate(fy, TaxRegime.NEW);
        Money rebate = Rebate87A.of(taxableIncome, taxBeforeRebate, rebateRule);
        Money taxAfterRebate = taxBeforeRebate.subtract(rebate);
        if (taxAfterRebate.isNegative()) {
            taxAfterRebate = Money.ZERO;
        }

        // Steps 6 & 7: Surcharge with marginal relief and cess
        List<CessSurchargeRule> surchargeBands = ruleReader.surchargeBands(fy, TaxRegime.NEW);
        CessSurchargeRule cessRule = ruleReader.cess(fy, TaxRegime.NEW);

        Function<Money, Money> thresholdTaxCalc =
                SurchargeAndCess.createThresholdTaxCalculator(slabs, rebateRule, surchargeBands);

        SurchargeAndCessResult scResult =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, surchargeBands, cessRule, thresholdTaxCalc);

        // Step 8: Net annual tax after previous employer TDS, rounded to the rupee
        Money prevTds = input.prevEmploymentTds();
        Money totalTaxBeforeTds = taxAfterRebate.add(scResult.surcharge()).add(scResult.cess());
        Money annualTaxRaw = totalTaxBeforeTds.subtract(prevTds);
        if (annualTaxRaw.isNegative()) {
            annualTaxRaw = Money.ZERO;
        }
        Money annualTax = Money.of(annualTaxRaw.raw().setScale(0, RoundingMode.HALF_UP));

        List<String> combinedAssumptions = new ArrayList<>(input.salary().assumptions());

        return new TaxComputation(
                TaxRegime.NEW,
                fy.label(),
                incomeFromSalary,
                prevIncome,
                standardDeduction,
                grossTotalIncome,
                taxableIncome,
                taxBeforeRebate,
                rebate,
                scResult.surcharge(),
                scResult.marginalRelief(),
                scResult.cess(),
                prevTds,
                annualTax,
                slabResult.lines(),
                combinedAssumptions);
    }
}
