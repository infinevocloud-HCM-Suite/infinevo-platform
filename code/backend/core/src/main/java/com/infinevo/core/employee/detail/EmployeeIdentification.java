package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The identity documents of one employee (W-13.2) — {@code core.employee_identification},
 * {@code migration/src/main/resources/db/migration/core/V017__employee_identification.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>HRMS is the whole shape here —
 * {@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:15-45}.
 * Payroll carries PAN only, on the personal row
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeePersonalDetail.java:33-39}),
 * and that PAN lands here: keeping the identity documents in one table is what lets the audit trail
 * and any future access rule name one place.
 *
 * <p><strong>Spelling: {@code aadhaarNumber}, never the frozen {@code aadharCardNumber}</strong>
 * ({@code Identification.java:20}). The database column name is what
 * {@code AuditWriter.REDACTED_FRAGMENTS} matches on, and {@code aadhaar} is the spelling that list
 * holds — renaming the column silently un-redacts it.
 *
 * <p><strong>{@link Audited}, and this table is why the redaction fix had to land first</strong>
 * (spec section 2, the founder's addition). W-22.1 shipped the capture mechanism and it had recorded
 * nothing, because no production class carried the annotation; the deferred half of it was that a
 * property mapping to more than one column fell back to its Java name and {@code String.valueOf},
 * which the column-name deny-list could never match. {@code aadhaar_number} and {@code pan_number}
 * are precisely the values that must not reach {@code core.audit_log} in clear, so the order was not
 * negotiable. Every column here is flat and single, and the deny-list matches both names —
 * {@code aadhaar} as a fragment, {@code pan} as a whole word in {@code pan_number}.
 */
@Entity
@Table(
        name = "employee_identification",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_employee_identification_tenant_employee",
                    columnList = "tenant_id, employee_id",
                    unique = true),
            @Index(name = "idx_employee_identification_tenant_pan", columnList = "tenant_id, pan_number"),
            @Index(name = "idx_employee_identification_tenant_aadhaar", columnList = "tenant_id, aadhaar_number")
        })
@Audited
public class EmployeeIdentification extends EmployeeDetail {

    @Column(name = "immigration_status", length = 64)
    private String immigrationStatus;

    @Column(name = "aadhaar_number", length = 12)
    private String aadhaarNumber;

    @Column(name = "pan_number", length = 10)
    private String panNumber;

    @Column(name = "personal_tax_id", length = 32)
    private String personalTaxId;

    @Column(name = "social_insurance_number", length = 64)
    private String socialInsuranceNumber;

    @Column(name = "id_proof_type", length = 64)
    private String idProofType;

    @Column(name = "id_document_name", length = 128)
    private String idDocumentName;

    @Column(name = "id_document_number", length = 64)
    private String idDocumentNumber;

    @Column(name = "address_proof_type", length = 64)
    private String addressProofType;

    @Column(name = "address_document_name", length = 128)
    private String addressDocumentName;

    @Column(name = "address_document_number", length = 64)
    private String addressDocumentNumber;

    protected EmployeeIdentification() {}

    EmployeeIdentification(UUID tenantId, Employee employee, String actor) {
        super(tenantId, employee, actor);
    }

    public String getImmigrationStatus() {
        return immigrationStatus;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public String getPersonalTaxId() {
        return personalTaxId;
    }

    public String getSocialInsuranceNumber() {
        return socialInsuranceNumber;
    }

    public String getIdProofType() {
        return idProofType;
    }

    public String getIdDocumentName() {
        return idDocumentName;
    }

    public String getIdDocumentNumber() {
        return idDocumentNumber;
    }

    public String getAddressProofType() {
        return addressProofType;
    }

    public String getAddressDocumentName() {
        return addressDocumentName;
    }

    public String getAddressDocumentNumber() {
        return addressDocumentNumber;
    }

    /**
     * Copies the identity numbers in and stamps the row.
     *
     * <p>Package-private, and called only by {@link EmployeeIdentificationServiceImpl} once it has
     * validated the request. Neither the id, the tenant nor the employee is reachable from here.
     */
    void applyIdentityNumbers(
            String immigrationStatus,
            String aadhaarNumber,
            String panNumber,
            String personalTaxId,
            String socialInsuranceNumber) {
        this.immigrationStatus = immigrationStatus;
        this.aadhaarNumber = aadhaarNumber;
        this.panNumber = panNumber;
        this.personalTaxId = personalTaxId;
        this.socialInsuranceNumber = socialInsuranceNumber;
    }

    /**
     * The two supporting documents — identity proof and address proof.
     *
     * <p>The files behind them are not stored here: document upload is W-21, the document store —
     * spec section 2, Out of scope. These columns say which document was produced, not where it is.
     */
    void applyDocuments(
            String idProofType,
            String idDocumentName,
            String idDocumentNumber,
            String addressProofType,
            String addressDocumentName,
            String addressDocumentNumber) {
        this.idProofType = idProofType;
        this.idDocumentName = idDocumentName;
        this.idDocumentNumber = idDocumentNumber;
        this.addressProofType = addressProofType;
        this.addressDocumentName = addressDocumentName;
        this.addressDocumentNumber = addressDocumentNumber;
    }
}
