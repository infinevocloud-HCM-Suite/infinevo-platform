package com.infinevo.core.employee;

import com.infinevo.core.org.Department;
import com.infinevo.core.org.Designation;
import com.infinevo.core.org.WorkLocation;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The neutral employee root record (W-13.1) — one row per employee per tenant.
 *
 * <p>Maps {@code core.employee} —
 * {@code migration/src/main/resources/db/migration/core/V010__employee.sql}. The schema is named on
 * the table: {@code code/backend/app/src/main/resources/application.yml} sets no
 * {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>It is a merge of two shapes that do not agree, not a copy of either — HRMS spreads ~67 columns
 * over six entities rooted at
 * {@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Employee.java:14-154},
 * Payroll holds ~56 over three rooted at
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:14}.
 * Three differences are deliberate and must survive review.
 *
 * <ul>
 *   <li><strong>{@code dateOfJoining} is a {@link LocalDate}, not a {@code String}.</strong> The
 *       frozen system stores it as text —
 *       {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:44}
 *       ({@code private String dateOfJoining; // Can be changed to LocalDate if needed}) — so no
 *       query can order, compare or bound by it. {@code terminationDate} is a date for the same
 *       reason.
 *   <li><strong>{@code employeeNumber} is unique within a tenant, never globally.</strong> The
 *       index is {@code (tenant_id, employee_number)} ({@code V010__employee.sql:47}). The frozen
 *       column is {@code unique = true} outright ({@code BasicDetails.java:24-25}), which would let
 *       one tenant numbering block another.
 *   <li><strong>No money and no floating-point field.</strong> {@code amountInPercentage} is a
 *       {@code double} at {@code BasicDetails.java:123}, which {@code docs/CONVENTIONS.md} section 2
 *       forbids; it is a payroll figure and is not carried here at all.
 * </ul>
 *
 * <p><strong>W-14.1 added {@code department}, {@code designation} and {@code workLocation}</strong>,
 * the three columns this class was written without. They are the expand half of expand / contract:
 * every one is nullable ({@code V014__employee_org_columns.sql}), so an employee created before that
 * ticket keeps working and nothing had to be backfilled. Making any of them {@code NOT NULL} is a
 * later contract step after a backfill, never an edit here.
 *
 * <p>Still absent on purpose: the PF / PT / LWF / ESI / EPS eligibility flags that sit on the frozen
 * employee row ({@code BasicDetails.java:76-140}) are payroll semantics going to a {@code payroll}
 * table under PAY-01 — spec section 2 and section 13, decision 1. Adding them here is a named risk,
 * not an economy.
 *
 * <p>Deletion is soft: {@link #markDeleted} sets the flag and every read path filters on it, so an
 * employee referenced by a past pay run or leave record is never orphaned by a DELETE.
 *
 * <p><strong>W-13.2 added {@link Audited}, and that is the point of the ticket.</strong> W-22.1
 * shipped the capture mechanism and it has recorded <em>nothing</em> since, because no production
 * class carried the annotation — its own spec section 2 records that {@code core.tenant} and
 * {@code core.user_tenant} could not be the proof, since both are reached by raw JDBC and a
 * Hibernate listener cannot observe them. This row and the five detail sections are the first that
 * can be. A change to a person's name, status, joining date or org assignment now produces a row in
 * {@code core.audit_log} — the soft delete included, which is an {@code UPDATE} of
 * {@code is_deleted} and not a {@code DELETE}, so the trail records who retired an employee.
 * Removing the annotation stops the capture; the rows already written stay readable.
 */
@Entity
@Table(
        name = "employee",
        schema = "core",
        indexes = {
            @Index(name = "idx_employee_tenant_employee_number", columnList = "tenant_id, employee_number"),
            @Index(name = "idx_employee_tenant_work_email", columnList = "tenant_id, work_email"),
            @Index(name = "idx_employee_tenant_status", columnList = "tenant_id, status"),
            @Index(name = "idx_employee_tenant_department_id", columnList = "tenant_id, department_id"),
            @Index(name = "idx_employee_tenant_designation_id", columnList = "tenant_id, designation_id"),
            @Index(name = "idx_employee_tenant_work_location_id", columnList = "tenant_id, work_location_id")
        })
@Audited
public class Employee {

    /** Written into {@code created_by} / {@code updated_by} when no authenticated user is on the thread. */
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Never taken from the request. The service reads it from {@code TenantContext}, which the
     * binding filter set from the verified token — spec section 3.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_number", nullable = false, length = 64)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "gender", length = 32)
    private String gender;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EmploymentStatus status;

    @Column(name = "work_email", length = 255)
    private String workEmail;

    @Column(name = "mobile", length = 32)
    private String mobile;

    /**
     * Defaults to {@code true} — founder decision 2, spec section 13. The frozen system leaves it
     * unset ({@code BasicDetails.java:75-76}) and makes an administrator enable each person, which
     * is onboarding friction with no security benefit once roles exist.
     */
    @Column(name = "is_portal_enabled", nullable = false)
    private boolean portalEnabled = true;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    /**
     * The org masters this employee is assigned to — W-14.1, {@code V014__employee_org_columns.sql}.
     *
     * <p>All three are nullable and lazy. Nullable because the columns were added expand-style to a
     * table that already had rows; lazy because loading an employee is not a reason to load three
     * more tables, and every read path here needs the id far more often than the name.
     *
     * <p>A foreign key alone does <strong>not</strong> stop one of these pointing at another tenant's
     * record: {@code migration_user} owns the tables, and PostgreSQL runs referential-integrity checks
     * as the owner, bypassing row-level security. {@link EmployeeServiceImpl} is what refuses it, and
     * {@code OrgMasterRlsIT} and {@code EmployeeAssignmentIT} are what prove it — spec section 7.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_location_id")
    private WorkLocation workLocation;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected Employee() {}

    Employee(UUID tenantId, String actor) {
        this.tenantId = tenantId;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public EmploymentStatus getStatus() {
        return status;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public String getMobile() {
        return mobile;
    }

    public boolean isPortalEnabled() {
        return portalEnabled;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public void setUserAccountId(UUID userAccountId) {
        this.userAccountId = userAccountId;
    }

    public Department getDepartment() {
        return department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public WorkLocation getWorkLocation() {
        return workLocation;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Copies the mutable fields in and stamps the row.
     *
     * <p>Package-private, and called only by {@link EmployeeServiceImpl} once it has validated the
     * request and checked the status transition. Neither {@code id} nor {@code tenantId} is
     * reachable from here: a row cannot change tenant, which is the whole point of reading the
     * tenant from the context rather than from the request.
     */
    void apply(
            String employeeNumber,
            String firstName,
            String middleName,
            String lastName,
            String gender,
            LocalDate dateOfJoining,
            LocalDate terminationDate,
            EmploymentStatus status,
            String workEmail,
            String mobile,
            boolean portalEnabled,
            String actor) {
        this.employeeNumber = employeeNumber;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.gender = gender;
        this.dateOfJoining = dateOfJoining;
        this.terminationDate = terminationDate;
        this.status = status;
        this.workEmail = workEmail;
        this.mobile = mobile;
        this.portalEnabled = portalEnabled;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    /**
     * Assigns the three org masters — W-14.1, spec section 3.
     *
     * <p>Separate from {@link #apply} rather than three more parameters on a method that already
     * takes eleven, and package-private for the same reason: {@link EmployeeServiceImpl} has already
     * resolved each id <em>inside the bound tenant</em> by the time it calls this. An entity that
     * accepted an id would have no way to check that, and the cross-tenant assignment the foreign key
     * cannot stop would arrive here looking legitimate.
     *
     * <p>Null clears the assignment, which is how an employee is moved out of a department without
     * being moved into another one.
     */
    void assign(Department department, Designation designation, WorkLocation workLocation) {
        this.department = department;
        this.designation = designation;
        this.workLocation = workLocation;
    }

    /**
     * Soft delete — the row stays, hidden from every read path.
     *
     * <p>A hard delete would orphan the pay runs, leave records and approvals that will point at
     * this employee, and would destroy history that a statutory filing has to be reproducible from.
     */
    void markDeleted(String actor) {
        this.deleted = true;
        // W-13.4: release the login. uk_employee_tenant_user_account (V026) covers deleted rows
        // too, so a link left on a deleted employee could never be moved to a rehire's new record.
        this.userAccountId = null;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
