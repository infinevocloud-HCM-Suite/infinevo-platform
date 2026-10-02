package com.infinevo.core.leave;

import java.time.LocalDate;

/**
 * Request payload for creating or updating a leave type (W-16.1, spec section 4).
 */
public record LeaveTypeRequest(
        String name,
        String code,
        Boolean isPaid,
        LeaveUnit unit,
        Boolean allowHalfDay,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean isActive,
        LeavePolicyRequest policy) {

    public LeaveTypeRequest(
            String name,
            String code,
            Boolean isPaid,
            LeaveUnit unit,
            Boolean allowHalfDay,
            LocalDate validFrom,
            LocalDate validTo) {
        this(name, code, isPaid, unit, allowHalfDay, validFrom, validTo, true, null);
    }
}
