package com.infinevo.core.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only interface for attendance queries across date ranges (W-39.1).
 * Consumed by W-18 loss-of-pay policy derivation.
 */
public interface AttendanceQuery {

    /**
     * Reads attendance records for an employee within a date range (inclusive), ordered by date ascending.
     *
     * @param employeeId the employee ID
     * @param from       start date (inclusive)
     * @param to         end date (inclusive)
     * @return list of attendance days
     */
    List<AttendanceDay> days(UUID employeeId, LocalDate from, LocalDate to);
}
