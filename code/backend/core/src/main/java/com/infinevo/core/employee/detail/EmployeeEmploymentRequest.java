package com.infinevo.core.employee.detail;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a client may say about an employee's working arrangement (W-13.2, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read.
 *
 * <p>This is a replace, not a patch. Every field omitted is written as null, which is how a value is
 * cleared.
 *
 * <p>Department, designation, joining date and termination date are <strong>not</strong> here: they
 * are on the root record — {@code PUT /api/v1/employees/{id}}. Nor are the three approver levels;
 * they go to W-14's reporting line (spec section 2, Out of scope).
 *
 * @param shiftStartTime a {@link LocalTime}. Jackson parses {@code "09:00"} into one and rejects
 *     {@code "9:00 AM"} — the frozen system takes the string as given
 *     ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java:35-36}),
 *     so both reach the database and no query can tell them apart.
 *     D-41: whole minutes only — {@code "09:00:30"} parses, and is refused as not {@code HH:mm}
 * @param shiftEndTime the same, and it must be after {@code shiftStartTime} when both are given
 * @param timeZone an IANA zone id {@link java.time.ZoneId#of} knows, such as {@code Asia/Kolkata}
 *     (D-41); free text is refused
 * @param employmentType D-40, {@link EmploymentType}
 * @param probationEndDate D-40; not before the employee's {@code dateOfJoining}
 * @param noticePeriodDays D-40; 0 to 365
 */
public record EmployeeEmploymentRequest(
        String payGrade,
        String workstationId,
        String timeZone,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        String note,
        EmploymentType employmentType,
        LocalDate probationEndDate,
        Integer noticePeriodDays) {

    /** The canonical constructor is the one Jackson binds — named, so the shorter one below cannot be picked. */
    @JsonCreator
    public EmployeeEmploymentRequest {}

    /**
     * The request as it was before D-40, with the three employment terms absent. Kept so the callers
     * written against the six-field shape still compile; absent means null, which on this replace
     * clears them.
     */
    public EmployeeEmploymentRequest(
            String payGrade,
            String workstationId,
            String timeZone,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            String note) {
        this(payGrade, workstationId, timeZone, shiftStartTime, shiftEndTime, note, null, null, null);
    }
}
