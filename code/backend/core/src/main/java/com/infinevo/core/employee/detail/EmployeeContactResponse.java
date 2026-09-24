package com.infinevo.core.employee.detail;

import java.time.Instant;
import java.util.UUID;

/**
 * An employee's contact section as the API returns it (W-13.2, spec section 4).
 *
 * <p>Output only: {@code id}, {@code tenantId}, {@code employeeId} and the two audit timestamps have
 * no counterpart on {@link EmployeeContactRequest}.
 */
public record EmployeeContactResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
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
        String familyDoctorContactNumber,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted section. The only way one of these is built. */
    public static EmployeeContactResponse from(EmployeeContact contact) {
        return new EmployeeContactResponse(
                contact.getId(),
                contact.getTenantId(),
                contact.getEmployee().getId(),
                contact.getPersonalEmail(),
                contact.getAlternateMobile(),
                contact.getAddressLine1(),
                contact.getAddressLine2(),
                contact.getCity(),
                contact.getState(),
                contact.getStateCode(),
                contact.getZipCode(),
                contact.getPermanentAddressLine1(),
                contact.getPermanentAddressLine2(),
                contact.getPermanentCity(),
                contact.getPermanentState(),
                contact.getPermanentStateCode(),
                contact.getPermanentZipCode(),
                contact.getEmergencyContactName(),
                contact.getEmergencyContactNumber(),
                contact.getEmergencyContactRelationship(),
                contact.getSecondaryEmergencyContactName(),
                contact.getSecondaryEmergencyContactNumber(),
                contact.getSecondaryEmergencyContactRelationship(),
                contact.getFamilyDoctorName(),
                contact.getFamilyDoctorContactNumber(),
                contact.getCreatedAt(),
                contact.getUpdatedAt());
    }
}
