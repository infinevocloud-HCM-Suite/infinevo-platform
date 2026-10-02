package com.infinevo.payroll.form16;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Breakdown of the tax calculation matching the 14 headline computation fields (W-36.4).
 */
public record TaxBreakdown(
        BigDecimal grossTotalIncome,
        BigDecimal hraExemption,
        BigDecimal standardDeduction,
        BigDecimal professionalTax,
        BigDecimal housePropertyIncome,
        BigDecimal otherIncome,
        BigDecimal chapterVia,
        BigDecimal taxableIncome,
        BigDecimal taxBeforeRebate,
        BigDecimal rebate,
        BigDecimal surcharge,
        BigDecimal cess,
        BigDecimal prevEmployerTds,
        BigDecimal annualTax) {

    public TaxBreakdown {
        grossTotalIncome = scale(grossTotalIncome);
        hraExemption = scale(hraExemption);
        standardDeduction = scale(standardDeduction);
        professionalTax = scale(professionalTax);
        housePropertyIncome = scale(housePropertyIncome);
        otherIncome = scale(otherIncome);
        chapterVia = scale(chapterVia);
        taxableIncome = scale(taxableIncome);
        taxBeforeRebate = scale(taxBeforeRebate);
        rebate = scale(rebate);
        surcharge = scale(surcharge);
        cess = scale(cess);
        prevEmployerTds = scale(prevEmployerTds);
        annualTax = scale(annualTax);
    }

    private static BigDecimal scale(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
