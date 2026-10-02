package com.infinevo.hrms.timesheet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/** One task's day entries under a project line (W-42.1). */
@Entity
@Table(name = "timesheet_task_entry", schema = "hrms")
public class TimesheetTaskEntry extends TimesheetRow {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_entry_id", nullable = false, updatable = false)
    private TimesheetProjectEntry projectEntry;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @OneToMany(mappedBy = "taskEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<TimesheetDayEntry> days = new ArrayList<>();

    protected TimesheetTaskEntry() {}

    TimesheetTaskEntry(UUID tenantId, TimesheetProjectEntry projectEntry, UUID taskId, String actor) {
        stamp(tenantId, actor);
        this.projectEntry = projectEntry;
        this.taskId = taskId;
    }

    /** Adds the hours worked on one day. */
    public TimesheetDayEntry addDay(LocalDate workDate, BigDecimal hours, String description, String actor) {
        TimesheetDayEntry entry = new TimesheetDayEntry(getTenantId(), this, workDate, hours, description, actor);
        days.add(entry);
        return entry;
    }

    public TimesheetProjectEntry getProjectEntry() {
        return projectEntry;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public List<TimesheetDayEntry> getDays() {
        return days;
    }
}
