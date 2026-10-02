package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Details of an employee who becomes overdrawn following a policy entitlement reduction (W-16.2, spec section 13).
 */
public record OverdrawnEmployee(
        UUID employeeId,
        UUID leaveTypeId,
        BigDecimal previousEntitlement,
        BigDecimal newEntitlement,
        BigDecimal overdrawnDays) {}
