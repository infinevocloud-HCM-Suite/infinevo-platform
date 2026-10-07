package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * The working arrangement of one employee (W-13.2) — {@code core.employee_employment},
 * {@code migration/src/main/resources/db/migration/core/V018__employee_employment.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>HRMS
 * ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java:17-36})
 * holds ten columns; only five of them are employment detail. The other four already have a home and
 * are <strong>not</strong> duplicated here — {@code department} and {@code jobTitle} are
 * {@code core.employee.department_id} and {@code designation_id} (W-14.1), {@code doj} and
 * {@code terminationDate} are {@code core.employee.date_of_joining} and {@code termination_date}
 * (W-13.1). A second copy of a joining date is how two answers to "when did this person start" get
 * written, and a payroll run cannot tell which is right.
 *
 * <p>{@code note} is {@code Report.java:27}. {@code Report.java}'s three approver columns
 * ({@code :22-24}) are <strong>not</strong> here: they belong to W-14's reporting line, by the split
 * decision — spec section 2, Out of scope, and section 9, which names pulling them in "while we are
 * here" as a medium risk.
 *
 * <p><strong>{@code shiftStartTime} and {@code shiftEndTime} are {@link LocalTime}, over real
 * {@code TIME} columns.</strong> {@code Work.java:35-36} types both as {@code String}, so nothing can
 * order or compare them and {@code "09:00"} and {@code "9:00 AM"} are both valid rows. This is the
 * same correction W-13.1 made to {@code dateOfJoining}, for the same reason.
 *
 * <p><strong>D-40 added {@code employmentType}, {@code probationEndDate} and
 * {@code noticePeriodDays}</strong> ({@code V154__employee_profile_columns.sql}). They are terms of
 * employment and this is the Employment section, so they live here and not on {@code core.employee};
 * the Add Employee form writes them through this section in the create transaction
 * ({@code EmployeeServiceImpl.create}). All three are nullable — the rows written before D-40 have
 * none.
 *
 * <p><strong>{@link Audited}</strong> for the reason {@link EmployeePersonal} gives: W-22.1 shipped
 * the capture mechanism and no production class carried the annotation, so it had captured nothing.
 */
@Entity
@Table(
        name = "employee_employment",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_employee_employment_tenant_employee",
                    columnList = "tenant_id, employee_id",
                    unique = true)
        })
@Audited
public class EmployeeEmployment extends EmployeeDetail {

    @Column(name = "pay_grade", length = 64)
    private String payGrade;

    @Column(name = "workstation_id", length = 64)
    private String workstationId;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 32)
    private EmploymentType employmentType;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    protected EmployeeEmployment() {}

    EmployeeEmployment(UUID tenantId, Employee employee, String actor) {
        super(tenantId, employee, actor);
    }

    public String getPayGrade() {
        return payGrade;
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalTime getShiftStartTime() {
        return shiftStartTime;
    }

    public LocalTime getShiftEndTime() {
        return shiftEndTime;
    }

    public String getNote() {
        return note;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public LocalDate getProbationEndDate() {
        return probationEndDate;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    /**
     * Copies the mutable fields in and stamps the row.
     *
     * <p>Package-private, and called only by {@link EmployeeEmploymentServiceImpl} once it has
     * validated the request. Neither the id, the tenant nor the employee is reachable from here.
     */
    void apply(
            String payGrade,
            String workstationId,
            String timeZone,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            String note,
            EmploymentType employmentType,
            LocalDate probationEndDate,
            Integer noticePeriodDays,
            String actor) {
        this.payGrade = payGrade;
        this.workstationId = workstationId;
        this.timeZone = timeZone;
        this.shiftStartTime = shiftStartTime;
        this.shiftEndTime = shiftEndTime;
        this.note = note;
        this.employmentType = employmentType;
        this.probationEndDate = probationEndDate;
        this.noticePeriodDays = noticePeriodDays;
        stamp(actor);
    }
}
