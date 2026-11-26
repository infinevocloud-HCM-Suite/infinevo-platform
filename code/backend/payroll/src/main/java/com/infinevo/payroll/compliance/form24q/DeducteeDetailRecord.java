package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record Type DD (Deductee Detail) for Form 24Q Annexure I.
 */
public record DeducteeDetailRecord(
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        String pan,
        String deducteeCode,
        String sectionCode,
        LocalDate paymentDate,
        BigDecimal grossAmount,
        BigDecimal tdsAmount,
        BigDecimal surchargeAmount,
        BigDecimal cessAmount,
        BigDecimal totalTaxDeducted,
        BigDecimal totalTaxDeposited,
        LocalDate deductionDate,
        String reasonForNonDeduction,
        String certificateNumber,
        UUID matchedChallanId,
        int challanSerialNumberInBatch) {}
