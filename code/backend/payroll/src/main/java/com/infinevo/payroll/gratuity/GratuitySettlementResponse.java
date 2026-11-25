package com.infinevo.payroll.gratuity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing a persisted {@link GratuitySettlement} (W-91).
 */
public record GratuitySettlementResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        LocalDate joiningDate,
        LocalDate exitDate,
        int completedYears,
        boolean exemptFrom5YrRule,
        BigDecimal lastDrawnBasic,
        BigDecimal lastDrawnDa,
        BigDecimal calculatedAmount,
        BigDecimal statutoryCeiling,
        BigDecimal grossGratuityPayable,
        BigDecimal taxExemptAmount,
        BigDecimal taxableAmount,
        GratuitySettlementStatus status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
