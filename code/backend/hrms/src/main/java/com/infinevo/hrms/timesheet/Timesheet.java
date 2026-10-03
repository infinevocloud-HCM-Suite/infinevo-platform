package com.infinevo.hrms.timesheet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * A weekly timesheet: one employee, one Monday-to-Sunday week (W-42.1).
 *
 * <p>The employee is the caller's own, never taken from a request body (the legacy fault at
 * {@code TimesheetServiceImpl.java:77-78}). Employee, project and task are held as ids only: names are not
 * copied beside them. Its project lines are owned by it and go with it.
 */
@Entity
@Table(name = "timesheet", schema = "hrms")
public class Timesheet extends TimesheetRow {

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "week_start_date", nullable = false, updatable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false, updatable = false)
    private LocalDate weekEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @OneToMany(mappedBy = "timesheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<TimesheetProjectEntry> projects = new ArrayList<>();

    protected Timesheet() {}

    /** A new draft for the week starting {@code weekStartDate}; the week ends six days later. */
    public Timesheet(UUID tenantId, UUID employeeId, LocalDate weekStartDate, String actor) {
        stamp(tenantId, actor);
        this.employeeId = employeeId;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekStartDate.plusDays(6);
    }

    /** Adds a project line, which is a draft like the timesheet that holds it. */
    public TimesheetProjectEntry addProject(UUID projectId, String actor) {
        TimesheetProjectEntry entry = new TimesheetProjectEntry(getTenantId(), this, projectId, actor);
        projects.add(entry);
        return entry;
    }

    /** Drops every project line (and, through them, every task and day), as a replace starts from nothing. */
    public void clearProjects() {
        projects.clear();
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public TimesheetStatus getStatus() {
        return status;
    }

    public void setStatus(TimesheetStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<TimesheetProjectEntry> getProjects() {
        return projects;
    }
}
