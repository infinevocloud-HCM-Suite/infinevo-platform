package com.phegondev.usersmanagementsystem.entity.timesheet;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

@Entity
@Table(name = "project_entry")
public class ProjectEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// Project reference details
	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "project_name")
	private String projectName;

	@Column(name = "rejection_reason", length = 1000)
	private String rejectionReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private TimesheetStatus status = TimesheetStatus.DRAFT;

	// Many projects belong to one timesheet
	@ManyToOne
	@JoinColumn(name = "timesheet_id") // FK in project_entry table
	private Timesheets timesheet;

	// One project has many tasks
	@OneToMany(mappedBy = "projectEntry", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TaskEntry> tasks = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	public TimesheetStatus getStatus() {
		return status;
	}

	public void setStatus(TimesheetStatus status) {
		this.status = status;
	}

	public Timesheets getTimesheet() {
		return timesheet;
	}

	public void setTimesheet(Timesheets timesheet) {
		this.timesheet = timesheet;
	}

	public List<TaskEntry> getTasks() {
		return tasks;
	}

	public void setTasks(List<TaskEntry> tasks) {
		this.tasks = tasks;
	}

}
