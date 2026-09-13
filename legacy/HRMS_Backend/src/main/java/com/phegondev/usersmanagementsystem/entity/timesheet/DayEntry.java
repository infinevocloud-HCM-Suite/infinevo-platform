package com.phegondev.usersmanagementsystem.entity.timesheet;

import java.time.DayOfWeek;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "day_entry")
public class DayEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Actual calendar date
    @Column(name = "date")
    private LocalDate date;

    // Enum for day of the week (MONDAY, TUESDAY, ...)
    @Enumerated(EnumType.STRING)
    @Column(name = "day_name")
    private DayOfWeek dayName;

    // Hours worked (e.g., 1.5, 8.0)
    @Column(name = "hours")
    private Float hours;

    // Work description
    @Column(name = "description")
    private String description;

    // Many DayEntries belong to one TaskEntry
    @ManyToOne
    @JoinColumn(name = "task_entry_id")
    private TaskEntry taskEntry;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public DayOfWeek getDayName() {
		return dayName;
	}

	public void setDayName(DayOfWeek dayName) {
		this.dayName = dayName;
	}

	public Float getHours() {
		return hours;
	}

	public void setHours(Float hours) {
		this.hours = hours;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TaskEntry getTaskEntry() {
		return taskEntry;
	}

	public void setTaskEntry(TaskEntry taskEntry) {
		this.taskEntry = taskEntry;
	}
    
    
}
