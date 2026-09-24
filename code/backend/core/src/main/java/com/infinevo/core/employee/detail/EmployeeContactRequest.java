package com.infinevo.core.employee.detail;

/**
 * What a client may say about an employee's contact section (W-13.2, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read.
 *
 * <p>This is a replace, not a patch. Every field omitted is written as null, which is how a value is
 * cleared.
 *
 * <p>The two addresses are twelve flat fields and not two nested objects, mirroring the twelve flat
 * columns. {@link EmployeeContact} explains why the columns are flat: an {@code @Embedded} address
 * is redacted wholesale by the audit trail, and a nested DTO over flat columns would only invite
 * someone to "tidy" the entity to match.
 *
 * <p>{@code workEmail} and {@code mobile} are not here. They belong to the root record — {@code PUT
 * /api/v1/employees/{id}} — because they identify the employee to the platform.
 */
public record EmployeeContactRequest(
        String personalEmail,
        String alternateMobile,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String stateCode,
        String zipCode,
        String permanentAddressLine1,
        String permanentAddressLine2,
        String permanentCity,
        String permanentState,
        String permanentStateCode,
        String permanentZipCode,
        String emergencyContactName,
        String emergencyContactNumber,
        String emergencyContactRelationship,
        String secondaryEmergencyContactName,
        String secondaryEmergencyContactNumber,
        String secondaryEmergencyContactRelationship,
        String familyDoctorName,
        String familyDoctorContactNumber) {}
