package com.infinevo.payroll.taxcalc.recalc;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service contract for publishing TDS figures to the pay run subsystem (W-36.1).
 *
 * <p>Implemented by {@code EmployeeTdsServiceImpl} when W-36.1 lands.
 * A default no-op bean is provided when W-36.1 is not on the classpath.
 */
public interface EmployeeTdsService {

    record TdsFigures(
            String regime,
            BigDecimal annualGross,
            BigDecimal annualTaxableIncome,
            BigDecimal annualTax,
            UUID declarationId,
            String note) {}

    void record(UUID employeeId, String financialYear, TdsFigures figures);
}
