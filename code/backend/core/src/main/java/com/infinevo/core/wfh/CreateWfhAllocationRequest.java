package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateWfhAllocationRequest(
        UUID employeeId,
        UUID policyId,
        String wfhYear,
        LocalDate yearStartDate,
        LocalDate yearEndDate,
        BigDecimal entitlementDays,
        BigDecimal proRateFactor) {}
