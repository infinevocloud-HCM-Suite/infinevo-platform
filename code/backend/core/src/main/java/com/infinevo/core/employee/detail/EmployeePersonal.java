package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The personal section of one employee (W-13.2) — {@code core.employee_personal},
 * {@code migration/src/main/resources/db/migration/core/V015__employee_personal.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>The union of two shapes, not a copy of either (spec section 1). HRMS
 * ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/personal.java:36-48})
 * gives date of birth, marital status, nationality and ethnicity; Payroll
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeePersonalDetail.java:30-45})
 * gives father's name, the differently-abled type and the full income-tax exemption flag.
 *
 * <p>{@code dateOfBirth} is a {@link LocalDate}. {@code personal.java:37} types it as a raw
 * {@code java.sql.Date}, so nothing can compare or bound by it — the same correction W-13.1 made to
 * {@code dateOfJoining}.
 *
 * <p><strong>{@link Audited}, and this is the point of the ticket.</strong> W-22.1 shipped the
 * capture mechanism and it has recorded nothing since, because no production class carried the
 * annotation — its own spec section 2 records that {@code core.tenant} and {@code core.user_tenant}
 * could not be the proof, since both are reached by raw JDBC and a Hibernate listener cannot observe
 * them. These five tables are the first that can be (W-13.2 spec section 2, added by the founder).
 */
@Entity
@Table(
        name = "employee_personal",
        schema = "core",
        indexes = {
            @Index(name = "idx_employee_personal_tenant_employee", columnList = "tenant_id, employee_id", unique = true)
        })
@Audited
public class EmployeePersonal extends EmployeeDetail {

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "marital_status", length = 32)
    private String maritalStatus;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Column(name = "ethnicity", length = 64)
    private String ethnicity;

    @Column(name = "father_name", length = 100)
    private String fatherName;

    @Column(name = "differently_abled_type", length = 64)
    private String differentlyAbledType;

    /**
     * Payroll's {@code isEligibleForFullTaxExemption} ({@code EmployeePersonalDetail.java:30-45}).
     * {@code NOT NULL DEFAULT false} in the column, so it is a primitive here: a {@link Boolean} would
     * let a null reach an insert and be rejected by the database rather than answered here.
     */
    @Column(name = "is_eligible_for_full_tax_exemption", nullable = false)
    private boolean eligibleForFullTaxExemption = false;

    protected EmployeePersonal() {}

    EmployeePersonal(UUID tenantId, Employee employee, String actor) {
        super(tenantId, employee, actor);
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public String getNationality() {
        return nationality;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public String getFatherName() {
        return fatherName;
    }

    public String getDifferentlyAbledType() {
        return differentlyAbledType;
    }

    public boolean isEligibleForFullTaxExemption() {
        return eligibleForFullTaxExemption;
    }

    /**
     * Copies the mutable fields in and stamps the row.
     *
     * <p>Package-private, and called only by {@link EmployeePersonalServiceImpl} once it has
     * validated the request. Neither the id, the tenant nor the employee is reachable from here: a
     * section cannot change tenant or change person, which is the whole point of reading the tenant
     * from the context rather than from the request.
     */
    void apply(
            LocalDate dateOfBirth,
            String maritalStatus,
            String nationality,
            String ethnicity,
            String fatherName,
            String differentlyAbledType,
            boolean eligibleForFullTaxExemption,
            String actor) {
        this.dateOfBirth = dateOfBirth;
        this.maritalStatus = maritalStatus;
        this.nationality = nationality;
        this.ethnicity = ethnicity;
        this.fatherName = fatherName;
        this.differentlyAbledType = differentlyAbledType;
        this.eligibleForFullTaxExemption = eligibleForFullTaxExemption;
        stamp(actor);
    }
}
