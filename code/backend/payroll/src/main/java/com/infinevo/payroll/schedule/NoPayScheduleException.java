package com.infinevo.payroll.schedule;

/**
 * Thrown when a tenant has no pay schedule configured, or when a requested pay period
 * requires a schedule that has not yet been set up (W-28 §4, §7).
 */
public class NoPayScheduleException extends RuntimeException {

    public NoPayScheduleException(String message) {
        super(message);
    }

    public NoPayScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
