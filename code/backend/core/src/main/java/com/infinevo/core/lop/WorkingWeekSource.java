package com.infinevo.core.lop;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

/**
 * Port answering the working days of the week for an employee within a tenant (W-18.1 §4, W-28).
 *
 * <p>Implemented in the {@code payroll} module from the tenant's pay schedule.
 * {@code core} never references {@code payroll.pay_schedule} directly.
 */
public interface WorkingWeekSource {

    /**
     * Returns the set of working days of the week for the given employee in the given tenant.
     */
    Set<DayOfWeek> weekdaysFor(UUID tenantId, UUID employeeId);
}
