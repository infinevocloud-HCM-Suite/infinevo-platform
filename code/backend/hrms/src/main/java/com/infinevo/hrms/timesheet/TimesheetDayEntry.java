package com.infinevo.hrms.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The hours worked on one day on one task (W-42.1).
 *
 * <p>Hours are a {@link BigDecimal} at scale 2, never a float: the frozen HRMS used {@code Float} with no limit
 * ({@code DayEntry.java:27-28}).
 */
@Entity
@Table(name = "timesheet_day_entry", schema = "hrms")
public class TimesheetDayEntry extends TimesheetRow {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_entry_id", nullable = false, updatable = false)
    private TimesheetTaskEntry taskEntry;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal hours;

    @Column(name = "description", length = 500)
    private String description;

    protected TimesheetDayEntry() {}

    TimesheetDayEntry(
            UUID tenantId,
            TimesheetTaskEntry taskEntry,
            LocalDate workDate,
            BigDecimal hours,
            String description,
            String actor) {
        stamp(tenantId, actor);
        this.taskEntry = taskEntry;
        this.workDate = workDate;
        this.hours = hours;
        this.description = description;
    }

    public TimesheetTaskEntry getTaskEntry() {
        return taskEntry;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public String getDescription() {
        return description;
    }
}
