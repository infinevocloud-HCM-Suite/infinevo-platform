package com.infinevo.payroll.taxcalc.model;

import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Complete income tax computation result carrying every intermediate figure (W-33.1 spec § 4 line 117).
 *
 * @param regime the tax regime computed (e.g. {@link TaxRegime#NEW})
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
        List<String> assumptions) {

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
    }
}
