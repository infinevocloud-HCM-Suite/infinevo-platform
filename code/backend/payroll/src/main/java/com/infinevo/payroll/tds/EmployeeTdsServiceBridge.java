package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxcalc.TaxRegime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adapter bridging the recalculation module's EmployeeTdsService contract
 * with the full TDS engine implementation (W-36.1, W-33.3).
 */
@Component
@Primary
public class EmployeeTdsServiceBridge implements com.infinevo.payroll.taxcalc.recalc.EmployeeTdsService {

    private final EmployeeTdsService delegate;

    public EmployeeTdsServiceBridge(EmployeeTdsService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void record(
            UUID employeeId,
            String financialYear,
            com.infinevo.payroll.taxcalc.recalc.EmployeeTdsService.TdsFigures figures) {
        if (figures == null) {
            return;
        }
        TaxRegime regime = figures.regime() != null ? TaxRegime.valueOf(figures.regime()) : TaxRegime.NEW;
        com.infinevo.payroll.tds.TdsFigures tdsFigures = new com.infinevo.payroll.tds.TdsFigures(
                regime,
                figures.annualGross(),
                figures.annualTaxableIncome(),
                figures.annualTax(),
                null,
                figures.declarationId(),
                figures.note());
        delegate.record(employeeId, financialYear, tdsFigures);
    }
}
