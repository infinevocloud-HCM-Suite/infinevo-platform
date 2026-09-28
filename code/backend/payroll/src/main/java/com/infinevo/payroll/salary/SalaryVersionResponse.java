package com.infinevo.payroll.salary;

import com.infinevo.payroll.fbp.FbpSummaryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload representing a dated salary structure version for an employee (W-26.2, W-27.2).
 */
public record SalaryVersionResponse(
        UUID id,
        UUID employeeId,
        LocalDate effectiveFrom,
        BigDecimal annualCtc,
        BigDecimal monthlyCtc,
        boolean cancelled,
        Instant cancelledAt,
        String notes,
        BigDecimal changeInPercent,
        List<SalaryComponentItemResponse> earnings,
        List<SalaryComponentItemResponse> benefits,
        List<SalaryComponentItemResponse> reimbursements,
        FbpSummaryResponse fbp) {

    public SalaryVersionResponse(
            UUID id,
            UUID employeeId,
            LocalDate effectiveFrom,
            BigDecimal annualCtc,
            BigDecimal monthlyCtc,
            boolean cancelled,
            Instant cancelledAt,
            String notes,
            BigDecimal changeInPercent,
            List<SalaryComponentItemResponse> earnings,
            List<SalaryComponentItemResponse> benefits,
            List<SalaryComponentItemResponse> reimbursements) {
        this(
                id,
                employeeId,
                effectiveFrom,
                annualCtc,
                monthlyCtc,
                cancelled,
                cancelledAt,
                notes,
                changeInPercent,
                earnings,
                benefits,
                reimbursements,
                null);
    }
}
