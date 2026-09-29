package com.infinevo.payroll.taxcalc.model;

import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.ChapterViaLine;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome.HousePropertyResult;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Complete income tax computation result carrying every intermediate figure (W-33.1 spec § 4, W-33.2 spec § 4).
 *
 * @param regime the tax regime computed (e.g. {@link TaxRegime#NEW}, {@link TaxRegime#OLD})
 * @param financialYear the financial year label (e.g. "2025-2026")
 * @param incomeFromSalary gross salary plus previous employer income
 * @param prevEmploymentIncome income from previous employers
 * @param standardDeduction statutory standard deduction applied (capped at income)
 * @param grossTotalIncome total gross income before deductions
 * @param taxableIncome net taxable income (rounded to the rupee)
 * @param taxBeforeRebate progressive tax across slabs
 * @param rebate Section 87A rebate
 * @param surcharge high-income surcharge after marginal relief
 * @param marginalRelief statutory marginal relief amount
 * @param cess 4% health & education cess
 * @param prevEmploymentTds TDS already deducted by previous employers
 * @param annualTax net annual tax payable (rounded to the rupee)
 * @param slabLines per-bracket taxable amounts and tax
 * @param assumptions audit assumptions made during projection and computation
 * @param hraExemption Section 10(13A) HRA exemption amount
 * @param professionalTax Section 16(iii) professional tax deducted
 * @param houseProperty Section 24 and 71(3A) house property income and loss details
 * @param otherIncome gross other sources income
 * @param chapterViaDeductions Chapter VI-A investment deductions allowed
 * @param interestDeduction Section 80TTA or 80TTB interest deduction
 * @param additionalHomeLoanInterest Section 80EE or 80EEA additional home loan interest deduction
 * @param chapterViaLines audit breakdown lines for Chapter VI-A working
 */
public record TaxComputation(
        TaxRegime regime,
        String financialYear,
        Money incomeFromSalary,
        Money prevEmploymentIncome,
        Money standardDeduction,
        Money grossTotalIncome,
        Money taxableIncome,
        Money taxBeforeRebate,
        Money rebate,
        Money surcharge,
        Money marginalRelief,
        Money cess,
        Money prevEmploymentTds,
        Money annualTax,
        List<SlabLine> slabLines,
        List<String> assumptions,
        Money hraExemption,
        Money professionalTax,
        HousePropertyResult houseProperty,
        Money otherIncome,
        Money chapterViaDeductions,
        Money interestDeduction,
        Money additionalHomeLoanInterest,
        List<ChapterViaLine> chapterViaLines) {

    public TaxComputation {
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(incomeFromSalary, "incomeFromSalary must not be null");
        Objects.requireNonNull(prevEmploymentIncome, "prevEmploymentIncome must not be null");
        Objects.requireNonNull(standardDeduction, "standardDeduction must not be null");
        Objects.requireNonNull(grossTotalIncome, "grossTotalIncome must not be null");
        Objects.requireNonNull(taxableIncome, "taxableIncome must not be null");
        Objects.requireNonNull(taxBeforeRebate, "taxBeforeRebate must not be null");
        Objects.requireNonNull(rebate, "rebate must not be null");
        Objects.requireNonNull(surcharge, "surcharge must not be null");
        Objects.requireNonNull(marginalRelief, "marginalRelief must not be null");
        Objects.requireNonNull(cess, "cess must not be null");
        Objects.requireNonNull(prevEmploymentTds, "prevEmploymentTds must not be null");
        Objects.requireNonNull(annualTax, "annualTax must not be null");
        slabLines = slabLines == null ? List.of() : List.copyOf(slabLines);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        hraExemption = hraExemption == null ? Money.ZERO : hraExemption;
        professionalTax = professionalTax == null ? Money.ZERO : professionalTax;
        houseProperty = houseProperty == null
                ? new HousePropertyResult(Money.ZERO, Money.ZERO, Money.ZERO, false)
                : houseProperty;
        otherIncome = otherIncome == null ? Money.ZERO : otherIncome;
        chapterViaDeductions = chapterViaDeductions == null ? Money.ZERO : chapterViaDeductions;
        interestDeduction = interestDeduction == null ? Money.ZERO : interestDeduction;
        additionalHomeLoanInterest = additionalHomeLoanInterest == null ? Money.ZERO : additionalHomeLoanInterest;
        chapterViaLines = chapterViaLines == null ? List.of() : List.copyOf(chapterViaLines);
    }

    public TaxComputation(
            TaxRegime regime,
            String financialYear,
            Money incomeFromSalary,
            Money prevEmploymentIncome,
            Money standardDeduction,
            Money grossTotalIncome,
            Money taxableIncome,
            Money taxBeforeRebate,
            Money rebate,
            Money surcharge,
            Money marginalRelief,
            Money cess,
            Money prevEmploymentTds,
            Money annualTax,
            List<SlabLine> slabLines,
            List<String> assumptions) {
        this(
                regime,
                financialYear,
                incomeFromSalary,
                prevEmploymentIncome,
                standardDeduction,
                grossTotalIncome,
                taxableIncome,
                taxBeforeRebate,
                rebate,
                surcharge,
                marginalRelief,
                cess,
                prevEmploymentTds,
                annualTax,
                slabLines,
                assumptions,
                Money.ZERO,
                Money.ZERO,
                new HousePropertyResult(Money.ZERO, Money.ZERO, Money.ZERO, false),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                List.of());
    }
}
