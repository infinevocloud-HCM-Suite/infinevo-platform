package com.infinevo.payroll.statutory.report;

import java.math.BigDecimal;

/**
 * State-level summary row for the Professional Tax Return (W-95).
 *
 * @param stateCode Two-letter state code or name
 * @param ptrcNumber State PT Registration Certificate number
 * @param totalEmployeesLiable Count of employees with PT deducted
 * @param totalPtDeducted Total PT deducted for the state
 */
public record PtStateSummary(
        String stateCode, String ptrcNumber, long totalEmployeesLiable, BigDecimal totalPtDeducted) {}
