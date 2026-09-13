package com.phegondev.usersmanagementsystem.dto.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

import jakarta.persistence.Column;

public class TimesheetDto {
    private String timesheetId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private TimesheetStatus status;
    private String employeeId;
    private String employeeName;
    private List<ProjectEntryDto> projects;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submitted_at;
    
    
    
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public LocalDateTime getSubmitted_at() {
		return submitted_at;
	}
	public void setSubmitted_at(LocalDateTime submitted_at) {
		this.submitted_at = submitted_at;
	}
	public String getTimesheetId() {
		return timesheetId;
	}
	public void setTimesheetId(String timesheetId) {
		this.timesheetId = timesheetId;
	}
	public LocalDate getWeekStartDate() {
		return weekStartDate;
	}
	public void setWeekStartDate(LocalDate weekStartDate) {
		this.weekStartDate = weekStartDate;
	}
	public LocalDate getWeekEndDate() {
		return weekEndDate;
	}
	public void setWeekEndDate(LocalDate weekEndDate) {
		this.weekEndDate = weekEndDate;
	}
	public TimesheetStatus getStatus() {
		return status;
	}
	public void setStatus(TimesheetStatus status) {
		this.status = status;
	}
	public String getEmployeeId() {
		return employeeId;
	}
	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}
	public String getEmployeeName() {
		return employeeName;
	}
	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
	}
	public List<ProjectEntryDto> getProjects() {
		return projects;
	}
	public void setProjects(List<ProjectEntryDto> projects) {
		this.projects = projects;
	}
	
	@Override
	public String toString() {
		return "TimesheetDto [timesheetId=" + timesheetId + ", weekStartDate=" + weekStartDate + ", weekEndDate="
				+ weekEndDate + ", status=" + status + ", employeeId=" + employeeId + ", employeeName=" + employeeName
				+ ", projects=" + projects + "]";
	}
    
    
}
