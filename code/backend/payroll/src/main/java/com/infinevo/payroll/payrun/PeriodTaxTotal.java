package com.infinevo.payroll.payrun;

import java.math.BigDecimal;

/**
 * Projection interface for tax lines summed by period (W-36.4).
 */
public interface PeriodTaxTotal {
    String getPeriod();

    BigDecimal getAmount();
}
