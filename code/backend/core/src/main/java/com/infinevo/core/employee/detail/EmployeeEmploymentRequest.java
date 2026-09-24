package com.infinevo.core.employee.detail;

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
 * @param shiftEndTime the same, and it must be after {@code shiftStartTime} when both are given
 */
public record EmployeeEmploymentRequest(
        String payGrade,
        String workstationId,
        String timeZone,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        String note) {}
