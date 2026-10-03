package com.infinevo.hrms.attendance;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for clock session operations (W-40.3 §4).
 */
public interface ClockService {

    int MAX_RANGE_DAYS = 93;

    /**
     * Opens a new clock session for the authenticated employee.
     *
     * @throws ClockConflictException if a session from today is already open
     * @throws org.springframework.security.access.AccessDeniedException if user is not linked to an employee
     */
    ClockSessionResponse clockIn();

    /**
     * Closes the active clock session for the authenticated employee and re-derives day attendance.
     *
     * @throws ClockConflictException if no session is open or session duration exceeds 24 hours
     * @throws org.springframework.security.access.AccessDeniedException if user is not linked to an employee
     */
    ClockOutResponse clockOut();

    /**
     * Returns today's clock status, open session, and worked minutes for the authenticated employee.
     */
    TodayResponse today();

    /**
     * Returns the caller's clock sessions in the given date range (newest first).
     *
     * @throws ValidationException if from is after to or range exceeds 93 days
     */
    List<ClockSessionResponse> mySessions(LocalDate from, LocalDate to);

    /**
     * Returns tenant clock sessions in the given date range, optionally filtered by employee ID (newest first).
     *
     * @throws ValidationException if from is after to or range exceeds 93 days
     */
    List<ClockSessionResponse> allSessions(LocalDate from, LocalDate to, UUID employeeId);

    /**
     * Thrown on conflicts such as duplicate clock-in or invalid clock-out (maps to HTTP 409).
     */
    class ClockConflictException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ClockConflictException(String message) {
            super(message);
        }
    }

    /**
     * Thrown on invalid request parameters such as invalid date ranges (maps to HTTP 400).
     */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ValidationException(String message) {
            super(message);
        }
    }
}
