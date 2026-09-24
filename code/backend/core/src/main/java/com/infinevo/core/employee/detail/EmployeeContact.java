package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The contact section of one employee — addresses and emergency contacts (W-13.2) —
 * {@code core.employee_contact},
 * {@code migration/src/main/resources/db/migration/core/V016__employee_contact.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p><strong>The two addresses are flat columns and not an {@code @Embedded} value object, and that
 * is not a style choice.</strong> An {@code @Embedded} property maps to several columns, so
 * Hibernate cannot name a single column for it; {@code AuditEventListener.columnNames} therefore
 * returns {@code AuditWriter.Column.unresolved} and {@code AuditWriter.values} withholds the value
 * outright. An embedded address would make <em>every</em> address change unreadable in
 * {@code core.audit_log} — the row would say {@code residentialAddress: ***} and nothing more, which
 * is exactly the change a human most often needs to see. Six repeated columns are the price of an
 * audit trail that says what actually changed. {@code V016__employee_contact.sql} records the same
 * reasoning against the schema.
 *
 * <p>The structure is Payroll's, not HRMS's (spec section 6). HRMS holds free text —
 * {@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Contact.java:15-31}
 * — while Payroll models {@code addressLine1}, {@code addressLine2}, {@code city}, {@code state},
 * {@code stateCode}, {@code zipCode}
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/ResidentialAddress.java:8-13}).
 * A statutory filing needs a state code and free text cannot supply one.
 *
 * <p>The permanent address is six more columns on this row rather than a second row typed by purpose
 * — spec section 13, decision 2. One row per employee keeps the RLS story and the unique index
 * simple.
 *
 * <p>{@code workEmail} and {@code mobile} are <strong>not</strong> here: they stay on the root record
 * ({@code V010__employee.sql}), because they identify the employee to the platform and the portal
 * login path reads them without loading a detail section.
 *
 * <p><strong>{@link Audited}</strong> for the reason {@link EmployeePersonal} gives: W-22.1 shipped
 * the capture mechanism and no production class carried the annotation, so it had captured nothing.
 */
@Entity
@Table(
        name = "employee_contact",
        schema = "core",
        indexes = {
            @Index(name = "idx_employee_contact_tenant_employee", columnList = "tenant_id, employee_id", unique = true)
        })
@Audited
public class EmployeeContact extends EmployeeDetail {

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    @Column(name = "alternate_mobile", length = 32)
    private String alternateMobile;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "zip_code", length = 16)
    private String zipCode;

    @Column(name = "permanent_address_line1", length = 255)
    private String permanentAddressLine1;

    @Column(name = "permanent_address_line2", length = 255)
    private String permanentAddressLine2;

    @Column(name = "permanent_city", length = 100)
    private String permanentCity;

    @Column(name = "permanent_state", length = 100)
    private String permanentState;

    @Column(name = "permanent_state_code", length = 10)
    private String permanentStateCode;

    @Column(name = "permanent_zip_code", length = 16)
    private String permanentZipCode;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_number", length = 32)
    private String emergencyContactNumber;

    @Column(name = "emergency_contact_relationship", length = 64)
    private String emergencyContactRelationship;

    @Column(name = "secondary_emergency_contact_name", length = 100)
    private String secondaryEmergencyContactName;

    @Column(name = "secondary_emergency_contact_number", length = 32)
    private String secondaryEmergencyContactNumber;

    @Column(name = "secondary_emergency_contact_relationship", length = 64)
    private String secondaryEmergencyContactRelationship;

    @Column(name = "family_doctor_name", length = 100)
    private String familyDoctorName;

    @Column(name = "family_doctor_contact_number", length = 32)
    private String familyDoctorContactNumber;

    protected EmployeeContact() {}

    EmployeeContact(UUID tenantId, Employee employee, String actor) {
        super(tenantId, employee, actor);
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public String getAlternateMobile() {
        return alternateMobile;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getPermanentAddressLine1() {
        return permanentAddressLine1;
    }

    public String getPermanentAddressLine2() {
        return permanentAddressLine2;
    }

    public String getPermanentCity() {
        return permanentCity;
    }

    public String getPermanentState() {
        return permanentState;
    }

    public String getPermanentStateCode() {
        return permanentStateCode;
    }

    public String getPermanentZipCode() {
        return permanentZipCode;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactNumber() {
        return emergencyContactNumber;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public String getSecondaryEmergencyContactName() {
        return secondaryEmergencyContactName;
    }

    public String getSecondaryEmergencyContactNumber() {
        return secondaryEmergencyContactNumber;
    }

    public String getSecondaryEmergencyContactRelationship() {
        return secondaryEmergencyContactRelationship;
    }

    public String getFamilyDoctorName() {
        return familyDoctorName;
    }

    public String getFamilyDoctorContactNumber() {
        return familyDoctorContactNumber;
    }

    /**
     * Copies the mutable fields in and stamps the row.
     *
     * <p>Split into four calls rather than one method taking twenty-two positional strings. Every
     * column on this table is a {@link String} of a similar shape, so a single {@code apply(...)}
     * would be twenty-two interchangeable arguments in which a transposed pair compiles, passes every
     * type check, and quietly writes the permanent city into the residential one. Four short calls,
     * each over one coherent block, is the same flat mapping with that hazard removed.
     *
     * <p>Package-private, and called only by {@link EmployeeContactServiceImpl} once it has validated
     * the request. Neither the id, the tenant nor the employee is reachable from here.
     */
    void applyContactDetails(String personalEmail, String alternateMobile) {
        this.personalEmail = personalEmail;
        this.alternateMobile = alternateMobile;
    }

    /** The residential address — {@code address_line1} through {@code zip_code}. */
    void applyResidentialAddress(
            String addressLine1, String addressLine2, String city, String state, String stateCode, String zipCode) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.stateCode = stateCode;
        this.zipCode = zipCode;
    }

    /** The permanent address — the same six columns again, prefixed {@code permanent_}. */
    void applyPermanentAddress(
            String addressLine1, String addressLine2, String city, String state, String stateCode, String zipCode) {
        this.permanentAddressLine1 = addressLine1;
        this.permanentAddressLine2 = addressLine2;
        this.permanentCity = city;
        this.permanentState = state;
        this.permanentStateCode = stateCode;
        this.permanentZipCode = zipCode;
    }

    /** The two emergency contacts and the family doctor. */
    void applyEmergencyContacts(
            String emergencyContactName,
            String emergencyContactNumber,
            String emergencyContactRelationship,
            String secondaryEmergencyContactName,
            String secondaryEmergencyContactNumber,
            String secondaryEmergencyContactRelationship,
            String familyDoctorName,
            String familyDoctorContactNumber) {
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactNumber = emergencyContactNumber;
        this.emergencyContactRelationship = emergencyContactRelationship;
        this.secondaryEmergencyContactName = secondaryEmergencyContactName;
        this.secondaryEmergencyContactNumber = secondaryEmergencyContactNumber;
        this.secondaryEmergencyContactRelationship = secondaryEmergencyContactRelationship;
        this.familyDoctorName = familyDoctorName;
        this.familyDoctorContactNumber = familyDoctorContactNumber;
    }
}
