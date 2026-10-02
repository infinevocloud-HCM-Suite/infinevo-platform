package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for creating a manual leave allocation (W-16.2, spec section 4).
 */
public record LeaveAllocationRequest(
        UUID employeeId,
        UUID leaveTypeId,
        String leaveYear,
        LocalDate yearStartDate,
        LocalDate yearEndDate,
        BigDecimal openingDays) {}
