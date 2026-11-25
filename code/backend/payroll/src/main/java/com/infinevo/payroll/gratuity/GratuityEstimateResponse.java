package com.infinevo.payroll.gratuity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Breakdown of gratuity eligibility, statutory amount, ceiling, and Section 10(10) tax split (W-91).
 */
public record GratuityEstimateResponse(
        UUID employeeId,
        LocalDate joiningDate,
        LocalDate exitDate,
        GratuityReason reason,
        boolean eligible,
        boolean exemptFrom5YrRule,
        int completedYears,
        BigDecimal lastDrawnBasic,
        BigDecimal lastDrawnDa,
        BigDecimal calculatedAmount,
        BigDecimal statutoryCeiling,
        BigDecimal grossGratuityPayable,
        BigDecimal taxExemptAmount,
        BigDecimal taxableAmount) {}
