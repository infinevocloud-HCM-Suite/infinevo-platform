package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record Type CD (Challan Detail) for Form 24Q Annexure I.
 */
public record ChallanDetailRecord(
        UUID challanId,
        int serialNumberInBatch,
        String bsrCode,
        String challanSerialNo,
        LocalDate tenderDate,
        BigDecimal tax,
        BigDecimal surcharge,
        BigDecimal cess,
        BigDecimal interest,
        BigDecimal fee,
        BigDecimal totalAmount,
        String minorHead,
        String chequeOrDdNumber,
        int deducteeCount) {}
