package com.infinevo.payroll.statutory.report;

import java.math.BigDecimal;

/**
 * One employee's row in the State-wise Professional Tax Return statement (W-95).
 *
 * @param state State code or name from work location
 * @param ptrcNumber State PT Registration Certificate number
 * @param employeeCode Employee number
 * @param employeeName Full name of employee
 * @param grossSalary Gross earnings for the pay period
 * @param ptDeducted Professional tax deducted
 */
public record PtReturnRow(
        String state,
        String ptrcNumber,
        String employeeCode,
        String employeeName,
        BigDecimal grossSalary,
        BigDecimal ptDeducted) {}
