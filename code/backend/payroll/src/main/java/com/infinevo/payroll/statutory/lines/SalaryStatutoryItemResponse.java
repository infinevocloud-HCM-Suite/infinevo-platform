package com.infinevo.payroll.statutory.lines;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response payload representing an allocated statutory line on a salary version (W-31.3).
 */
public record SalaryStatutoryItemResponse(
        UUID id,
        String componentCode,
        ContributionShare share,
        BigDecimal wageBase,
        BigDecimal rate,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount,
        boolean includedInCtc) {

    public static SalaryStatutoryItemResponse from(CtcEpfComponent epf) {
        return new SalaryStatutoryItemResponse(
                epf.getId(),
                epf.getComponentCode().name(),
                epf.getShare(),
                epf.getWageBase(),
                epf.getRate(),
                epf.getMonthlyAmount(),
                epf.getAnnualAmount(),
                epf.isIncludedInCtc());
    }

    public static SalaryStatutoryItemResponse from(CtcEsiComponent esi) {
        return new SalaryStatutoryItemResponse(
                esi.getId(),
                esi.getComponentCode().name(),
                esi.getShare(),
                esi.getWageBase(),
                esi.getRate(),
                esi.getMonthlyAmount(),
                esi.getAnnualAmount(),
                esi.isIncludedInCtc());
    }
}
