package com.infinevo.core.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing attendance records (W-39.1).
 */
public interface AttendanceService {

    /**
     * Bulk upserts 1–1000 attendance records for the current tenant.
     *
     * @param entries the list of entries to upsert
     * @return the saved records
     */
    List<AttendanceResponse> upsert(List<AttendanceEntry> entries);

    /**
     * Lists attendance records for the current tenant within the specified date range.
     *
     * @param from       start date (inclusive)
     * @param to         end date (inclusive)
     * @param employeeId optional employee ID filter
     * @return attendance records ordered by date descending, employee
     */
    List<AttendanceResponse> list(LocalDate from, LocalDate to, UUID employeeId);

    /**
     * Deletes a single attendance record by ID within the current tenant.
     *
     * @param id the attendance record ID
     */
    void delete(UUID id);

    /**
     * Writes an attendance status for an employee on a given date from the clock seam (W-40.2).
     *
     * <p>If no attendance record exists for the date, inserts a new record with source {@link AttendanceSource#CLOCK}.
     * If an existing record has source {@link AttendanceSource#CLOCK}, updates its status.
     * If an existing record has source {@link AttendanceSource#ADMIN}, performs no write and returns the unchanged record.
     *
     * @param employeeId the employee ID
     * @param date the attendance date (cannot be after tenant clock today)
     * @param status the attendance status
     * @return result indicating whether the day was written and the final status/source
     */
    ClockDayResult recordFromClock(UUID employeeId, LocalDate date, AttendanceStatus status);

    /**
     * Thrown when an attendance record is not found in the current tenant.
     */
    class NotFoundException extends RuntimeException {
        public NotFoundException(UUID id) {
            super("Attendance record not found: " + id);
        }
    }
}
