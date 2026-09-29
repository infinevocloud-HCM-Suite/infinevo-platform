package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.ChapterViaResult;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome.HousePropertyResult;
import com.infinevo.payroll.taxcalc.engine.HraExemption;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraExemptionResult;
import com.infinevo.payroll.taxcalc.engine.HraExemption.RentPeriod;
import com.infinevo.payroll.taxcalc.engine.OtherIncomeAndInterest;
import com.infinevo.payroll.taxcalc.engine.OtherIncomeAndInterest.OtherIncomeResult;
import com.infinevo.payroll.taxcalc.engine.Rebate87A;
import com.infinevo.payroll.taxcalc.engine.SalaryDeductions;
import com.infinevo.payroll.taxcalc.engine.SalaryDeductions.SalaryDeductionsResult;
import com.infinevo.payroll.taxcalc.engine.SlabTax;
import com.infinevo.payroll.taxcalc.engine.SurchargeAndCess;
import com.infinevo.payroll.taxcalc.model.SlabTaxResult;
import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
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
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Income tax calculator for the Old Tax Regime with exemptions and Section deductions (W-33.2 spec § 3, § 4).
 *
 * <p>Implements the ten-step statutory calculation pipeline:
 * <ol>
 *   <li>Projected salary, basic, and HRA from active CTC versions</li>
 *   <li>Section 10(13A) HRA exemption month by month across rent periods</li>
 *   <li>Section 16 salary deductions (standard deduction and professional tax)</li>
 *   <li>Income from salary = salary − HRA exemption − std deduction − PT + prev employment income</li>
 *   <li>House property income/loss under Section 24 and Section 71(3A) combined loss set-off cap</li>
 *   <li>Other sources gross income</li>
 *   <li>Gross Total Income = max(0, incomeFromSalary + houseProperty + otherIncome)</li>
 *   <li>Chapter VI-A deductions (80C group, 80CCD(1B), 80D, 80E, 80TTA/80TTB, 80EE/80EEA)</li>
 *   <li>Taxable income = grossTotalIncome − Chapter VI-A, rounded to rupee</li>
 *   <li>Slabs by (fy, OLD, ageCategory), rebate, surcharge with marginal relief, cess, TDS</li>
 * </ol>
 */
@Component
public class OldRegimeCalculator implements RegimeCalculator {

    private final TaxRuleReader ruleReader;
    private final Section6AItemReader section6AItemReader;

    public OldRegimeCalculator(TaxRuleReader ruleReader, Section6AItemReader section6AItemReader) {
        this.ruleReader = Objects.requireNonNull(ruleReader, "ruleReader must not be null");
        this.section6AItemReader = Objects.requireNonNull(section6AItemReader, "section6AItemReader must not be null");
    }

    @Override
    public TaxRegime regime() {
        return TaxRegime.OLD;
    }

    @Override
    public TaxComputation compute(TaxInput input, FinancialYear fy) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(fy, "fy must not be null");

        // Step 1: Gross projected salary and previous employment income
        Money salary = input.salary().annualTaxableSalary();
        Money prevIncome = input.prevEmploymentIncome();

        // Step 2: Section 10(13A) HRA exemption
        HraRule hraRule = ruleReader.hra(fy);
        List<RentPeriod> rentPeriods = new ArrayList<>();
        if (input.houseRent() != null) {
            for (var rent : input.houseRent()) {
                rentPeriods.add(RentPeriod.from(rent));
            }
        }
        HraExemptionResult hraResult =
                HraExemption.calculate(input.isStayingInRentedHouse(), hraRule, input.hraSalaries(), rentPeriods);
        Money hraExemption = hraResult.totalExemption();

        // Step 3 & 4: Section 16 salary deductions and income from salary
        StandardDeductionRule stdRule = ruleReader.standardDeduction(fy, TaxRegime.OLD);
        Money pt = input.resolvedProfessionalTax();
        SalaryDeductionsResult salResult = SalaryDeductions.calculate(salary, hraExemption, stdRule, pt, prevIncome);
        Money standardDeduction = salResult.standardDeduction();
        Money incomeFromSalary = salResult.incomeFromSalary();

        // Step 5: House property income / loss under Section 24 and Section 71(3A)
        HomeLoanRule rule24B = ruleReader.homeLoan(fy, "24B", "INTEREST", "SELF_OCCUPIED");
        LetOutRule letOutRule = ruleReader.letOut(fy);
        HousePropertyResult hpResult = HousePropertyIncome.calculate(
                input.isRepayingSelfOccupiedLoan(),
                input.hasLetOutProperty(),
                rule24B,
                letOutRule,
                input.homeLoans(),
                input.letOutProperties());
        Money housePropertyIncome = hpResult.housePropertyIncome();

        // Step 6: Other sources income and interest deductions (80TTA / 80TTB)
        OtherIncomeRule rule80Tta = ruleReader.otherIncomeRule(fy, "80TTA");
        OtherIncomeRule rule80Ttb = ruleReader.otherIncomeRule(fy, "80TTB");
        OtherIncomeResult otherResult =
                OtherIncomeAndInterest.calculate(input.otherIncome(), input.ageCategory(), rule80Tta, rule80Ttb);
        Money otherIncome = otherResult.totalOtherIncome();
        Money interestDeduction = otherResult.interestDeduction();

        // Step 7: Gross Total Income
        Money rawGti = incomeFromSalary.add(housePropertyIncome).add(otherIncome);
        Money grossTotalIncome = rawGti.isNegative() ? Money.ZERO : rawGti;

        // Step 8: Chapter VI-A deductions
        BigDecimal raw80cCap = section6AItemReader.groupCap("80C_GROUP");
        Money group80cCap = Money.of(raw80cCap);

        Money nps1bCap = null;
        for (var item : section6AItemReader.activeItems("OLD")) {
            if ("80CCD(1B)".equals(item.sectionCode())) {
                nps1bCap = item.maxLimit() != null ? Money.of(item.maxLimit()) : null;
                break;
            }
        }

        List<HomeLoanRule> allLoanRules = ruleReader.homeLoanRules(fy);
        List<HomeLoanRule> additionalLoanRules = new ArrayList<>();
        for (HomeLoanRule rule : allLoanRules) {
            if ("80EE".equals(rule.sectionCode()) || "80EEA".equals(rule.sectionCode())) {
                additionalLoanRules.add(rule);
            }
        }

        ChapterViaResult viaResult = ChapterViaDeductions.calculate(
                input.section6A(),
                input.resolvedEmployeePf(),
                input.vpf(),
                input.employeeNps(),
                nps1bCap,
                group80cCap,
                input.homeLoans(),
                rule24B,
                additionalLoanRules,
                interestDeduction,
                grossTotalIncome);

        Money chapterViaInvestments = viaResult.chapterViaDeductions();
        Money additionalHomeLoanInterest = viaResult.additionalHomeLoanInterest();
        Money totalChapterViaAllowed = viaResult.totalAllowed();

        // Step 9: Taxable income rounded to the rupee
        Money taxableRaw = grossTotalIncome.subtract(totalChapterViaAllowed);
        if (taxableRaw.isNegative()) {
            taxableRaw = Money.ZERO;
        }
        Money taxableIncome = Money.of(taxableRaw.raw().setScale(0, RoundingMode.HALF_UP));

        // Step 10: Slabs, Section 87A rebate, surcharge, cess, and TDS
        List<TaxSlabDetail> slabs = ruleReader.slabs(fy, TaxRegime.OLD, input.ageCategory());
        SlabTaxResult slabResult = SlabTax.of(taxableIncome, slabs);
        Money taxBeforeRebate = slabResult.totalTax();

        Section87aRebateRule rebateRule = ruleReader.rebate(fy, TaxRegime.OLD);
        Money rebate = Rebate87A.of(taxableIncome, taxBeforeRebate, rebateRule);
        Money taxAfterRebate = taxBeforeRebate.subtract(rebate);
        if (taxAfterRebate.isNegative()) {
            taxAfterRebate = Money.ZERO;
        }

        List<CessSurchargeRule> surchargeBands = ruleReader.surchargeBands(fy, TaxRegime.OLD);
        CessSurchargeRule cessRule = ruleReader.cess(fy, TaxRegime.OLD);

        Function<Money, Money> thresholdTaxCalc = threshold -> {
            SlabTaxResult thSlab = SlabTax.of(threshold, slabs);
            Money thRebate = Rebate87A.of(threshold, thSlab.totalTax(), rebateRule);
            Money thNet = thSlab.totalTax().subtract(thRebate);
            return thNet.isNegative() ? Money.ZERO : thNet;
        };

        SurchargeAndCessResult scResult =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, surchargeBands, cessRule, thresholdTaxCalc);

        Money prevTds = input.prevEmploymentTds();
        Money totalTaxBeforeTds = taxAfterRebate.add(scResult.surcharge()).add(scResult.cess());
        Money annualTaxRaw = totalTaxBeforeTds.subtract(prevTds);
        if (annualTaxRaw.isNegative()) {
            annualTaxRaw = Money.ZERO;
        }
        Money annualTax = Money.of(annualTaxRaw.raw().setScale(0, RoundingMode.HALF_UP));

        List<String> combinedAssumptions = new ArrayList<>(input.salary().assumptions());
        if (hpResult.lossCapApplied()) {
            combinedAssumptions.add("House property loss capped to statutory set-off limit under Section 71(3A)");
        }
        if (totalChapterViaAllowed.compareTo(grossTotalIncome) == 0 && !grossTotalIncome.isZero()) {
            combinedAssumptions.add("Chapter VI-A deductions limited to Gross Total Income under Section 80A(2)");
        }

        return new TaxComputation(
                TaxRegime.OLD,
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
                combinedAssumptions,
                hraExemption,
                pt,
                hpResult,
                otherIncome,
                chapterViaInvestments,
                interestDeduction,
                additionalHomeLoanInterest,
                viaResult.lines());
    }
}
