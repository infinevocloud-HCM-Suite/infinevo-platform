package com.phegondev.usersmanagementsystem.dto;
 
import java.time.LocalDate;
import java.time.LocalDateTime;
 
public class TimesheetDTO {
	
	    private Long id;
	    private String timesheetId;
	    private String empId;
	    private String empName;
	    private Long projectId;
	    private Long taskId;
	    private String projectName;
	    private String taskName;
	    private String timeCategory;
	    private String resourcePlan;
 
	    private Float mondayHours;
	    private Float tuesdayHours;
	    private Float wednesdayHours;
	    private Float thursadyHours;
	    private Float fridayHours;
	    private Float saturdayHours;
	    private Float sundayHours;
 
	    private String comments;
 
	    private LocalDateTime createdAt;
	    private LocalDateTime updatedAt;
	    private String status;
	    private LocalDateTime submitted_at;
 
	    private LocalDate weekStart;
	    private LocalDate weekEnd;
		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
		}
		public String getTimesheetId() {
			return timesheetId;
		}
		public void setTimesheetId(String timesheetId) {
			this.timesheetId = timesheetId;
		}
		public String getEmpId() {
			return empId;
		}
		public void setEmpId(String empId) {
			this.empId = empId;
		}
		public String getEmpName() {
			return empName;
		}
		public void setEmpName(String empName) {
			this.empName = empName;
		}
		public Long getProjectId() {
			return projectId;
		}
		public void setProjectId(Long projectId) {
			this.projectId = projectId;
		}
		public Long getTaskId() {
			return taskId;
		}
		public void setTaskId(Long taskId) {
			this.taskId = taskId;
		}
		public String getProjectName() {
			return projectName;
		}
		public void setProjectName(String projectName) {
			this.projectName = projectName;
		}
		public String getTaskName() {
			return taskName;
		}
		public void setTaskName(String taskName) {
			this.taskName = taskName;
		}
		public String getTimeCategory() {
			return timeCategory;
		}
		public void setTimeCategory(String timeCategory) {
			this.timeCategory = timeCategory;
		}
		public String getResourcePlan() {
			return resourcePlan;
		}
		public void setResourcePlan(String resourcePlan) {
			this.resourcePlan = resourcePlan;
		}
		public Float getMondayHours() {
			return mondayHours;
		}
		public void setMondayHours(Float mondayHours) {
			this.mondayHours = mondayHours;
		}
		public Float getTuesdayHours() {
			return tuesdayHours;
		}
		public void setTuesdayHours(Float tuesdayHours) {
			this.tuesdayHours = tuesdayHours;
		}
		public Float getWednesdayHours() {
			return wednesdayHours;
		}
		public void setWednesdayHours(Float wednesdayHours) {
			this.wednesdayHours = wednesdayHours;
		}
		public Float getThursadyHours() {
			return thursadyHours;
		}
		public void setThursadyHours(Float thursadyHours) {
			this.thursadyHours = thursadyHours;
		}
		public Float getFridayHours() {
			return fridayHours;
		}
		public void setFridayHours(Float fridayHours) {
			this.fridayHours = fridayHours;
		}
		public Float getSaturdayHours() {
			return saturdayHours;
		}
		public void setSaturdayHours(Float saturdayHours) {
			this.saturdayHours = saturdayHours;
		}
		public Float getSundayHours() {
			return sundayHours;
		}
		public void setSundayHours(Float sundayHours) {
			this.sundayHours = sundayHours;
		}
		public String getComments() {
			return comments;
		}
		public void setComments(String comments) {
			this.comments = comments;
		}
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
		public String getStatus() {
			return status;
		}
		public void setStatus(String status) {
			this.status = status;
		}
		public LocalDateTime getSubmitted_at() {
			return submitted_at;
		}
		public void setSubmitted_at(LocalDateTime submitted_at) {
			this.submitted_at = submitted_at;
		}
		public LocalDate getWeekStart() {
			return weekStart;
		}
		public void setWeekStart(LocalDate weekStart) {
			this.weekStart = weekStart;
		}
		public LocalDate getWeekEnd() {
			return weekEnd;
		}
		public void setWeekEnd(LocalDate weekEnd) {
			this.weekEnd = weekEnd;
		}
		@Override
		public String toString() {
			return "TimesheetDTO [id=" + id + ", timesheetId=" + timesheetId + ", empId=" + empId + ", empName="
					+ empName + ", projectId=" + projectId + ", taskId=" + taskId + ", projectName=" + projectName
					+ ", taskName=" + taskName + ", timeCategory=" + timeCategory + ", resourcePlan=" + resourcePlan
					+ ", mondayHours=" + mondayHours + ", tuesdayHours=" + tuesdayHours + ", wednesdayHours="
					+ wednesdayHours + ", thursadyHours=" + thursadyHours + ", fridayHours=" + fridayHours
					+ ", saturdayHours=" + saturdayHours + ", sundayHours=" + sundayHours + ", comments=" + comments
					+ ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", status=" + status + ", submitted_at="
					+ submitted_at + ", weekStart=" + weekStart + ", weekEnd=" + weekEnd + "]";
		}
	    
	    
	    
    
 
}
 