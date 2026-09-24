package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The identification section (W-13.2) — {@code core.employee_identification},
 * {@code V017__employee_identification.sql}.
 *
 * <p>The read / write flow, the tenant, the employee check and the create-or-replace rule are all on
 * {@link AbstractEmployeeDetailServiceImpl}. What is here is what only this section has: its column
 * widths and the two document shapes worth checking.
 *
 * <p>PAN and Aadhaar are checked against the frozen patterns, verbatim —
 * {@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:19}
 * for Aadhaar ({@code ^\d{12}$}) and {@code :23} for PAN ({@code ^[A-Z]{5}[0-9]{4}[A-Z]{1}$}).
 * <strong>A lower-case PAN is refused rather than upper-cased on the way in.</strong> Normalising
 * would change the value a caller sent without telling it, and the frozen system refuses it too — so
 * a record that was legal there stays legal here, and one that was not stays refused.
 *
 * <p>Uniqueness of PAN or Aadhaar is <strong>not</strong> checked, here or in the schema. The frozen
 * PAN column is {@code unique = true} globally across every customer
 * ({@code EmployeePersonalDetail.java:33}), which is the defect W-13.1 corrected for
 * {@code employee_number}. Tenant-scoped uniqueness is the correct rule but cannot be turned on
 * before the rows that violate it are known — W-67 owns that, and it holds the data.
 */
@Service
public class EmployeeIdentificationServiceImpl
        extends AbstractEmployeeDetailServiceImpl<
                EmployeeIdentification, EmployeeIdentificationRequest, EmployeeIdentificationResponse>
        implements EmployeeIdentificationService {

    // Every width is the one in V017__employee_identification.sql.
    private static final int MAX_IMMIGRATION_STATUS = 64;
    private static final int MAX_AADHAAR = 12;
    private static final int MAX_PAN = 10;
    private static final int MAX_PERSONAL_TAX_ID = 32;
    private static final int MAX_SOCIAL_INSURANCE = 64;
    private static final int MAX_PROOF_TYPE = 64;
    private static final int MAX_DOCUMENT_NAME = 128;
    private static final int MAX_DOCUMENT_NUMBER = 64;

    /** {@code Identification.java:19} — twelve digits and nothing else. */
    private static final String AADHAAR_PATTERN = "\\d{12}";

    /** {@code Identification.java:23} — five letters, four digits, one letter, upper case. */
    private static final String PAN_PATTERN = "[A-Z]{5}[0-9]{4}[A-Z]";

    public EmployeeIdentificationServiceImpl(
            EmployeeIdentificationRepository repository, EmployeeRepository employees) {
        super(repository, employees);
    }

    @Override
    protected String kind() {
        return "identification";
    }

    @Override
    protected String uniqueEmployeeIndexName() {
        return "idx_employee_identification_tenant_employee";
    }

    @Override
    protected EmployeeIdentification newEntity(UUID tenantId, Employee employee, String actor) {
        return new EmployeeIdentification(tenantId, employee, actor);
    }

    @Override
    protected EmployeeIdentificationResponse toResponse(EmployeeIdentification entity) {
        return EmployeeIdentificationResponse.from(entity);
    }

    @Override
    protected void validate(EmployeeIdentificationRequest request, Map<String, String> errors) {
        optional(errors, "immigrationStatus", request.immigrationStatus(), MAX_IMMIGRATION_STATUS);
        optionalPattern(errors, "aadhaarNumber", request.aadhaarNumber(), MAX_AADHAAR, AADHAAR_PATTERN, "12 digits");
        optionalPattern(
                errors,
                "panNumber",
                request.panNumber(),
                MAX_PAN,
                PAN_PATTERN,
                "in the form AAAAA9999A, in upper case");
        optional(errors, "personalTaxId", request.personalTaxId(), MAX_PERSONAL_TAX_ID);
        optional(errors, "socialInsuranceNumber", request.socialInsuranceNumber(), MAX_SOCIAL_INSURANCE);
        optional(errors, "idProofType", request.idProofType(), MAX_PROOF_TYPE);
        optional(errors, "idDocumentName", request.idDocumentName(), MAX_DOCUMENT_NAME);
        optional(errors, "idDocumentNumber", request.idDocumentNumber(), MAX_DOCUMENT_NUMBER);
        optional(errors, "addressProofType", request.addressProofType(), MAX_PROOF_TYPE);
        optional(errors, "addressDocumentName", request.addressDocumentName(), MAX_DOCUMENT_NAME);
        optional(errors, "addressDocumentNumber", request.addressDocumentNumber(), MAX_DOCUMENT_NUMBER);
    }

    @Override
    protected void applyTo(EmployeeIdentification entity, EmployeeIdentificationRequest request, String actor) {
        entity.applyIdentityNumbers(
                trimToNull(request.immigrationStatus()),
                trimToNull(request.aadhaarNumber()),
                trimToNull(request.panNumber()),
                trimToNull(request.personalTaxId()),
                trimToNull(request.socialInsuranceNumber()));
        entity.applyDocuments(
                trimToNull(request.idProofType()),
                trimToNull(request.idDocumentName()),
                trimToNull(request.idDocumentNumber()),
                trimToNull(request.addressProofType()),
                trimToNull(request.addressDocumentName()),
                trimToNull(request.addressDocumentNumber()));
        entity.stamp(actor);
    }
}
