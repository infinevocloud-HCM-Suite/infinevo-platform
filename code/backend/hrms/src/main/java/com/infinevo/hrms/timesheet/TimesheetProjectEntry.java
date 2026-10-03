package com.infinevo.hrms.timesheet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * One project's lines on a timesheet (W-42.1). It carries its own status and rejection reason so that W-42.3 can
 * approve or reject each project separately (founder decision 2026-10-02) without adding a column.
 */
@Entity
@Table(name = "timesheet_project_entry", schema = "hrms")
public class TimesheetProjectEntry extends TimesheetRow {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timesheet_id", nullable = false, updatable = false)
    private Timesheet timesheet;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @OneToMany(mappedBy = "projectEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<TimesheetTaskEntry> tasks = new ArrayList<>();

    protected TimesheetProjectEntry() {}

    TimesheetProjectEntry(UUID tenantId, Timesheet timesheet, UUID projectId, String actor) {
        stamp(tenantId, actor);
        this.timesheet = timesheet;
        this.projectId = projectId;
    }

    /** Adds a task line to this project line. */
    public TimesheetTaskEntry addTask(UUID taskId, String actor) {
        TimesheetTaskEntry entry = new TimesheetTaskEntry(getTenantId(), this, taskId, actor);
        tasks.add(entry);
        return entry;
    }

    public Timesheet getTimesheet() {
        return timesheet;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public TimesheetStatus getStatus() {
        return status;
    }

    public void setStatus(TimesheetStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public List<TimesheetTaskEntry> getTasks() {
        return tasks;
    }
}
