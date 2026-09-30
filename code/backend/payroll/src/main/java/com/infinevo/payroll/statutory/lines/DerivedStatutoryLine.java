package com.infinevo.payroll.statutory.lines;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;

/**
 * Pure value object holding a derived statutory line calculation (W-31.3).
 */
public record DerivedStatutoryLine(
        StatutoryComponentCode componentCode,
        ContributionShare share,
        Money wageBase,
        BigDecimal rate,
        Money monthlyAmount,
        Money annualAmount,
        boolean includedInCtc) {

    public StatutoryComponentCode code() {
        return componentCode;
    }

    public boolean isEpf() {
        return componentCode == StatutoryComponentCode.EPF_EMPLOYEE
                || componentCode == StatutoryComponentCode.EPF_EMPLOYER
                || componentCode == StatutoryComponentCode.EPS_EMPLOYER
                || componentCode == StatutoryComponentCode.EDLI
                || componentCode == StatutoryComponentCode.EPF_ADMIN;
    }

    public boolean isEsi() {
        return componentCode == StatutoryComponentCode.ESI_EMPLOYEE
                || componentCode == StatutoryComponentCode.ESI_EMPLOYER;
    }
}
