package com.infinevo.payroll.schedule;

import com.infinevo.core.lop.NoLopPolicyException;

/**
 * Thrown when a tenant has no pay schedule configured, or when a requested pay period
 * requires a schedule that has not yet been set up (W-28 §4, §7).
 *
 * <p>A {@link NoLopPolicyException} (payroll → core is the allowed direction): when the
 * {@code core} working-day calculator reads the work week through {@link PayScheduleWorkingWeekSource},
 * a missing schedule reaches {@code /api/v1/lop-policy/basis} and W-29 in the same {@code 409} shape
 * as a missing policy, rather than as an unhandled {@code 500}.
 */
public class NoPayScheduleException extends NoLopPolicyException {

    public NoPayScheduleException(String message) {
        super(message);
    }

    public NoPayScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
