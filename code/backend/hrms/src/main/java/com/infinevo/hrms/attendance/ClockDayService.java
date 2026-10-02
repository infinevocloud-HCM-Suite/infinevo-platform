package com.infinevo.hrms.attendance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Seam for deriving a day's attendance status from its clock sessions (W-40.3 §4).
 *
 * <p>Run at every clock-out, and re-run by regularization (W-40.4) after corrections.
 */
public interface ClockDayService {

    /**
     * Derives the worked minutes and attendance status for an employee on a date, writes it to
     * {@code core.attendance} through {@code AttendanceService.recordFromClock}, and returns the result.
     *
     * @param employeeId the employee ID
     * @param date the attendance calendar date
     * @return the derived day result including whether it was written to attendance
     */
    ClockDayResponse rederive(UUID employeeId, LocalDate date);
}
