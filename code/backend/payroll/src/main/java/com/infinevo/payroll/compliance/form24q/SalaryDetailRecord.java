package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record Type SD (Salary Detail) for Form 24Q Annexure II (W-89.3).
 */
public record SalaryDetailRecord(
        UUID employeeId,
        int serialNumberInBatch,
        String employeeNumber,
        String employeeName,
        String pan,
        String regime,
        LocalDate employmentFrom,
        LocalDate employmentTo,
        BigDecimal grossSalary,
        BigDecimal exemptionsSection10,
        BigDecimal standardDeduction,
        BigDecimal professionalTax,
        BigDecimal totalDeductionsSection16,
        BigDecimal incomeChargeableUnderSalaries,
        BigDecimal housePropertyIncome,
        BigDecimal otherIncome,
        BigDecimal grossTotalIncome,
        BigDecimal chapterViaDeductions,
        BigDecimal totalTaxableIncome,
        BigDecimal taxOnTotalIncome,
        BigDecimal rebate87A,
        BigDecimal surcharge,
        BigDecimal cess,
        BigDecimal relief89,
        BigDecimal totalTaxPayable,
        BigDecimal totalTdsDeducted,
        BigDecimal prevEmployerTds,
        BigDecimal shortfallOrExcess) {}
