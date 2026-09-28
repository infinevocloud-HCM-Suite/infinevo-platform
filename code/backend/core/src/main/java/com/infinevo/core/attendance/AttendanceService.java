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
     * Thrown when an attendance record is not found in the current tenant.
     */
    class NotFoundException extends RuntimeException {
        public NotFoundException(UUID id) {
            super("Attendance record not found: " + id);
        }
    }
}
