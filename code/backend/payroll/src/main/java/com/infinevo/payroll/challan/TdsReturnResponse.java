package com.infinevo.payroll.challan;

import java.time.Instant;
import java.time.LocalDate;

/** A filed quarter (W-89). */
public record TdsReturnResponse(
        String financialYear,
        String quarter,
        String tokenNumber,
        LocalDate filedOn,
        Instant createdAt,
        String createdBy) {

    static TdsReturnResponse from(TdsReturn r) {
        return new TdsReturnResponse(
                r.getFinancialYear(),
                r.getQuarter(),
                r.getTokenNumber(),
                r.getFiledOn(),
                r.getCreatedAt(),
                r.getCreatedBy());
    }
}
