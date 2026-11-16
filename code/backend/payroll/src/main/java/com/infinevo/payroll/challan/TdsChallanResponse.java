package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing a TDS challan with its allocated total (W-89.1).
 */
public record TdsChallanResponse(
        UUID id,
        UUID tenantId,
        String financialYear,
        String quarter,
        String bsrCode,
        String challanSerialNo,
        LocalDate tenderDate,
        BigDecimal taxAmount,
        BigDecimal surchargeAmount,
        BigDecimal cessAmount,
        BigDecimal interestAmount,
        BigDecimal feePenaltyAmount,
        BigDecimal totalAmount,
        BigDecimal allocatedAmount,
        BigDecimal unallocatedAmount,
        String minorHead,
        boolean filed,
        String remarks,
        Instant createdAt,
        Instant updatedAt) {}
