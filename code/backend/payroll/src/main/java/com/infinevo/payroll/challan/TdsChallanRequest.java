package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload to create or update an ITNS 281 TDS challan (W-89.1).
 */
public record TdsChallanRequest(
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
        String minorHead,
        String remarks) {}
