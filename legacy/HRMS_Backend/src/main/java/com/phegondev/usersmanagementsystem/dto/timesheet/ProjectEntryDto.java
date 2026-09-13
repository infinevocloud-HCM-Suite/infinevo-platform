package com.phegondev.usersmanagementsystem.dto.timesheet;

import java.time.LocalDate;
import java.util.List;

import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

public class ProjectEntryDto {
	private Long projectId;
	private String projectName;
	private TimesheetStatus status; // Optional per project
	private List<TaskEntryDto> tasks;
	private String rejectionReason;

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

	public List<TaskEntryDto> getTasks() {
		return tasks;
	}

	public void setTasks(List<TaskEntryDto> tasks) {
		this.tasks = tasks;
	}

	@Override
	public String toString() {
		return "ProjectEntryDto [projectId=" + projectId + ", projectName=" + projectName + ", status=" + status
				+ ", tasks=" + tasks + "]";
	}

	public ProjectEntryDto() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ProjectEntryDto(Long projectId, String projectName) {
		super();
		this.projectName = projectName;
		this.projectId = projectId;
	}

}
