package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A TDS challan with how much of it is allocated to employees (W-89.1). {@code allocatableAmount} is tax +
 * surcharge + cess; interest and fee are paid on the challan but never split across employees, so
 * "fully allocated" means the allocatable amount is covered.
 */
public record TdsChallanResponse(
        UUID id,
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
        BigDecimal allocatableAmount,
        BigDecimal allocatedAmount,
        BigDecimal unallocatedAmount,
        AllocationStatus allocationStatus,
        String minorHead,
        boolean filed,
        String remarks,
        Instant createdAt,
        Instant updatedAt) {

    public enum AllocationStatus {
        UNALLOCATED,
        PARTIAL,
        FULL
    }
}
