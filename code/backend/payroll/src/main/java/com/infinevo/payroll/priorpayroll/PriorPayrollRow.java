package com.infinevo.payroll.priorpayroll;

/**
 * Raw parsed CSV row for a prior payroll month entry (W-38.1 §4).
 */
public record PriorPayrollRow(
        int lineNumber,
        String employeeNumber,
        String period,
        String grossEarnings,
        String epf,
        String esi,
        String pt,
        String tds,
        String netPay) {}
