package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.util.List;

/**
 * Annual preview summary and employee dataset for Form 24Q Annexure II (W-89.3).
 */
public record Annexure2PreviewResponse(
        String financialYear,
        String assessmentYear,
        String tan,
        String pan,
        String companyName,
        int totalEmployees,
        BigDecimal totalGrossSalary,
        BigDecimal totalTaxableIncome,
        BigDecimal totalTaxPayable,
        BigDecimal totalTdsDeducted,
        BigDecimal totalShortfall,
        List<SalaryDetailRecord> records) {}
