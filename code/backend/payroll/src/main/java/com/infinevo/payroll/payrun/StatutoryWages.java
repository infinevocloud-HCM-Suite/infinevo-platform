package com.infinevo.payroll.payrun;

import java.math.BigDecimal;

/**
 * Persisted statutory calculation wage bases for the employee (W-87 §2).
 */
public record StatutoryWages(BigDecimal epfWage, BigDecimal epsWage, BigDecimal edliWage) {}
