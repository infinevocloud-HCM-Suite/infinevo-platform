package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateWfhPolicyRequest(
        String code,
        String name,
        BigDecimal annualDays,
        BigDecimal monthlyLimitDays,
        boolean allowHalfDay,
        int advanceNoticeDays,
        Integer maxConsecutiveDays,
        boolean requiresManagerApproval,
        boolean requiresHrApproval,
        LocalDate effectiveFrom,
        boolean active) {}
