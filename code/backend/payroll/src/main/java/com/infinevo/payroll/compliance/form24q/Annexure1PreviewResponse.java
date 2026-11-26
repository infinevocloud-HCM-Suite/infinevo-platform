package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Preview summary and dataset for Form 24Q Annexure I.
 */
public record Annexure1PreviewResponse(
        String financialYear,
        String quarter,
        String assessmentYear,
        String tan,
        String pan,
        String companyName,
        int totalChallans,
        BigDecimal totalChallanAmount,
        int totalDeductees,
        BigDecimal totalGrossPaid,
        BigDecimal totalTdsDeducted,
        BigDecimal totalTdsDeposited,
        boolean isReconciled,
        BigDecimal unallocatedTds,
        int missingPanCount,
        List<UUID> missingPanEmployeeIds,
        List<ChallanDetailRecord> challans,
        List<DeducteeDetailRecord> deductees,
        List<String> portalFileProblems) {}
