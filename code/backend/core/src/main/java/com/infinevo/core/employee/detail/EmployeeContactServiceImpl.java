package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The contact section (W-13.2) — {@code core.employee_contact}, {@code V016__employee_contact.sql}.
 *
 * <p>The read / write flow, the tenant, the employee check and the create-or-replace rule are all on
 * {@link AbstractEmployeeDetailServiceImpl}. What is here is what only this section has: its column
 * widths, the email shape, and how its request lands on its entity.
 *
 * <p>The two addresses are validated twice, field for field, because they are twelve separate
 * columns — {@link EmployeeContact} says why they are not one embedded value object.
 */
@Service
public class EmployeeContactServiceImpl
        extends AbstractEmployeeDetailServiceImpl<EmployeeContact, EmployeeContactRequest, EmployeeContactResponse>
        implements EmployeeContactService {

    // Every width is the one in V016__employee_contact.sql.
    private static final int MAX_EMAIL = 255;
    private static final int MAX_PHONE = 32;
    private static final int MAX_ADDRESS_LINE = 255;
    private static final int MAX_CITY = 100;
    private static final int MAX_STATE = 100;
    private static final int MAX_STATE_CODE = 10;
    private static final int MAX_ZIP_CODE = 16;
    private static final int MAX_PERSON_NAME = 100;
    private static final int MAX_RELATIONSHIP = 64;

    public EmployeeContactServiceImpl(EmployeeContactRepository repository, EmployeeRepository employees) {
        super(repository, employees);
    }

    @Override
    protected String kind() {
        return "contact";
    }

    @Override
    protected String uniqueEmployeeIndexName() {
        return "idx_employee_contact_tenant_employee";
    }

    @Override
    protected EmployeeContact newEntity(UUID tenantId, Employee employee, String actor) {
        return new EmployeeContact(tenantId, employee, actor);
    }

    @Override
    protected EmployeeContactResponse toResponse(EmployeeContact entity) {
        return EmployeeContactResponse.from(entity);
    }

    @Override
    protected void validate(EmployeeContactRequest request, Map<String, String> errors) {
        optionalEmail(errors, "personalEmail", request.personalEmail(), MAX_EMAIL);
        optional(errors, "alternateMobile", request.alternateMobile(), MAX_PHONE);

        optional(errors, "addressLine1", request.addressLine1(), MAX_ADDRESS_LINE);
        optional(errors, "addressLine2", request.addressLine2(), MAX_ADDRESS_LINE);
        optional(errors, "city", request.city(), MAX_CITY);
        optional(errors, "state", request.state(), MAX_STATE);
        optional(errors, "stateCode", request.stateCode(), MAX_STATE_CODE);
        optional(errors, "zipCode", request.zipCode(), MAX_ZIP_CODE);

        optional(errors, "permanentAddressLine1", request.permanentAddressLine1(), MAX_ADDRESS_LINE);
        optional(errors, "permanentAddressLine2", request.permanentAddressLine2(), MAX_ADDRESS_LINE);
        optional(errors, "permanentCity", request.permanentCity(), MAX_CITY);
        optional(errors, "permanentState", request.permanentState(), MAX_STATE);
        optional(errors, "permanentStateCode", request.permanentStateCode(), MAX_STATE_CODE);
        optional(errors, "permanentZipCode", request.permanentZipCode(), MAX_ZIP_CODE);

        optional(errors, "emergencyContactName", request.emergencyContactName(), MAX_PERSON_NAME);
        optional(errors, "emergencyContactNumber", request.emergencyContactNumber(), MAX_PHONE);
        optional(errors, "emergencyContactRelationship", request.emergencyContactRelationship(), MAX_RELATIONSHIP);
        optional(errors, "secondaryEmergencyContactName", request.secondaryEmergencyContactName(), MAX_PERSON_NAME);
        optional(errors, "secondaryEmergencyContactNumber", request.secondaryEmergencyContactNumber(), MAX_PHONE);
        optional(
                errors,
                "secondaryEmergencyContactRelationship",
                request.secondaryEmergencyContactRelationship(),
                MAX_RELATIONSHIP);
        optional(errors, "familyDoctorName", request.familyDoctorName(), MAX_PERSON_NAME);
        optional(errors, "familyDoctorContactNumber", request.familyDoctorContactNumber(), MAX_PHONE);
    }

    @Override
    protected void applyTo(EmployeeContact entity, EmployeeContactRequest request, String actor) {
        entity.applyContactDetails(trimToNull(request.personalEmail()), trimToNull(request.alternateMobile()));
        entity.applyResidentialAddress(
                trimToNull(request.addressLine1()),
                trimToNull(request.addressLine2()),
                trimToNull(request.city()),
                trimToNull(request.state()),
                trimToNull(request.stateCode()),
                trimToNull(request.zipCode()));
        entity.applyPermanentAddress(
                trimToNull(request.permanentAddressLine1()),
                trimToNull(request.permanentAddressLine2()),
                trimToNull(request.permanentCity()),
                trimToNull(request.permanentState()),
                trimToNull(request.permanentStateCode()),
                trimToNull(request.permanentZipCode()));
        entity.applyEmergencyContacts(
                trimToNull(request.emergencyContactName()),
                trimToNull(request.emergencyContactNumber()),
                trimToNull(request.emergencyContactRelationship()),
                trimToNull(request.secondaryEmergencyContactName()),
                trimToNull(request.secondaryEmergencyContactNumber()),
                trimToNull(request.secondaryEmergencyContactRelationship()),
                trimToNull(request.familyDoctorName()),
                trimToNull(request.familyDoctorContactNumber()));
        entity.stamp(actor);
    }
}
