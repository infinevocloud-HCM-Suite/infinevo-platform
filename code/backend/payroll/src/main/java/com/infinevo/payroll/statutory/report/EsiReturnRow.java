package com.infinevo.payroll.statutory.report;

import java.math.BigDecimal;

/**
 * One employee's row in the monthly ESI Return (W-95).
 *
 * @param ipNumber IP (Insurance Person) number from employee statutory profile
 * @param ipName Full name of employee
 * @param paidDays Number of days worked (paid days)
 * @param totalMonthlyWages Total monthly gross wages
 * @param ipContribution Employee ESI contribution (0.75%)
 * @param employerContribution Employer ESI contribution (3.25%)
 * @param totalContribution Total ESI contribution
 * @param reasonCodeZeroDays Reason code if zero paid days
 */
public record EsiReturnRow(
        String ipNumber,
        String ipName,
        BigDecimal paidDays,
        BigDecimal totalMonthlyWages,
        BigDecimal ipContribution,
        BigDecimal employerContribution,
        BigDecimal totalContribution,
        String reasonCodeZeroDays) {}
