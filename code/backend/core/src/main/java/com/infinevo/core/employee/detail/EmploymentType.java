package com.infinevo.core.employee.detail;

/**
 * The kind of employment (D-40) — {@code core.employee_employment.employment_type},
 * {@code migration/src/main/resources/db/migration/core/V154__employee_profile_columns.sql}.
 *
 * <p>Stored as the string name, never as an ordinal. An ordinal survives a reordering of this file
 * silently and turns every existing row into a different type.
 *
 * <p>The frozen form calls it "Employment Status" and offers four free-text values —
 * {@code Full-Time Permanent}, {@code Contract}, {@code Part-Time}, {@code Internship}
 * ({@code legacy/HRMS_Frontend/src/components/employee/AddEmployee.jsx:930-933}) — stored as typed
 * in {@code personal.employmentStatus}
 * ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/personal.java:18}).
 * Those four map to {@link #PERMANENT}, {@link #CONTRACT}, {@link #PART_TIME} and {@link #INTERN};
 * {@link #PROBATION} is the payroll spelling
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/test/ExcelTestController.java:216}),
 * and {@link #CONSULTANT} covers the engaged-not-employed case neither frozen app names. Here the
 * column carries a {@code CHECK} with exactly these names, so a row written outside the service
 * cannot hold a value this enum cannot read.
 */
public enum EmploymentType {
    PERMANENT,
    CONTRACT,
    PART_TIME,
    INTERN,
    PROBATION,
    CONSULTANT
}
