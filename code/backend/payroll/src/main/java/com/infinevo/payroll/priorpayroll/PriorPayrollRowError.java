package com.infinevo.payroll.priorpayroll;

/**
 * Validation or parsing error for a prior payroll CSV row (W-38.1 §4).
 */
public record PriorPayrollRowError(int lineNumber, String employeeNumber, String period, String reason) {}
