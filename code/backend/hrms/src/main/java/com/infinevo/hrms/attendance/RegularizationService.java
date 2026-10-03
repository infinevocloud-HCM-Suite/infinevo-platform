package com.infinevo.hrms.attendance;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Attendance regularization requests (W-40.4 §4): submit one, read my own, read the tenant's.
 */
public interface RegularizationService {

    int MAX_RANGE_DAYS = 93;

    /** The polymorphic subject table the approval instance points at. */
    String SUBJECT_TABLE = "hrms.attendance_regularization";

    /**
     * Saves a {@code PENDING} request for the caller and starts the {@code REGULARIZATION} approval flow, in one
     * transaction.
     *
     * @throws ValidationException when a rule of §4 "Rules at submit" maps to {@code 400}
     * @throws ConflictException when a pending request exists for the date, or the day was set by an administrator
     * @throws org.springframework.security.access.AccessDeniedException if the login is not linked to an employee
     */
    RegularizationResponse submit(RegularizationRequest request);

    /** The caller's requests whose date is in the range, newest first. */
    List<RegularizationResponse> mine(LocalDate from, LocalDate to);

    /** The tenant's requests whose date is in the range, optionally by status and employee, newest first. */
    List<RegularizationResponse> all(LocalDate from, LocalDate to, RegularizationStatus status, UUID employeeId);

    /** A submit or read refused as invalid input (maps to {@code 400 VALIDATION_FAILED}). */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ValidationException(String message) {
            super(message);
        }
    }

    /** A submit refused because of the state of the day (maps to {@code 409 CONFLICT}). */
    class ConflictException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ConflictException(String message) {
            super(message);
        }
    }
}
