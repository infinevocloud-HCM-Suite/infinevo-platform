package com.infinevo.core.employee.detail;

import java.time.LocalDate;

/**
 * What a client may say about an employee's personal section (W-13.2, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read. A
 * tenant a caller can state is a tenant a caller can change, which is the single thing the tenancy
 * design exists to prevent.
 *
 * <p>Nor is there an {@code id}, an {@code employeeId} or any audit field. The employee is the path
 * variable, identity is the server's, and there is no separate creation step: {@code PUT} is the only
 * write, and it creates the row the first time and replaces it after that.
 *
 * <p>This is a replace, not a patch. Every field omitted is written as null, which is how a value is
 * cleared — there is no other way to clear one.
 *
 * @param eligibleForFullTaxExemption null means false. The column is {@code NOT NULL DEFAULT false}.
 */
public record EmployeePersonalRequest(
        LocalDate dateOfBirth,
        String maritalStatus,
        String nationality,
        String ethnicity,
        String fatherName,
        String differentlyAbledType,
        Boolean eligibleForFullTaxExemption) {}
