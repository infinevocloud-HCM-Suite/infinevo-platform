package com.phegondev.usersmanagementsystem.entity;
 
import java.time.LocalDate;
import java.time.LocalDateTime;
 
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
 
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
 
@Entity
@Table(name = "timesheet")
public class Timesheet {
	
	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;
	    
	    @Column(name ="timesheet_id", nullable = false, unique = true)
	    private String timesheetId;
	    
	    @Column(name ="emp_id", nullable = false)
	    private String empId;
	    
	    @Column(name ="emp_name", nullable = false)
	    private String empName;
	    
	    @Column(name ="project_id", nullable = false)
	    private Long projectId;
	    
	    @Column(name ="task_id", nullable = false)
	    private Long taskId;
	    
	    @Column(name = "project_name", nullable = false)
	    private String projectName;
 
	    @Column(name = "task_name", nullable = false)
	    private String taskName;
 
	    
	    @Column(name ="time_category", nullable = false)
	    private String timeCategory;
	    
	    @Column(name ="resource_plan", nullable = false)
	    private String resourcePlan;
	    
	    @Column(name ="monday_hours")
	    private Float mondayHours;
	    
	    @Column(name ="tuesday_hours")
	    private Float tuesdayHours;
	    
	    @Column(name ="wednesday_hours")
	    private Float wednesdayHours;
	    
	    @Column(name ="thursady_hours")
	    private Float thursadyHours;
	    
	    @Column(name ="friday_hours")
	    private Float fridayHours;
	
	    
	    @Column(name ="saturday_hours")
	    private Float saturdayHours;
	    
	    @Column(name ="sunday_hours")
	    private Float sundayHours;
	    
	    @Column(name ="comments")
	    private String comments;
	    
	    @CreationTimestamp
	    @Column(name = "created_at", updatable = false)
	    private LocalDateTime createdAt;
 
	    @UpdateTimestamp
	    @Column(name = "updated_at")
	    private LocalDateTime updatedAt;
	    
	    @Column(name = "status", nullable = false)
	    private String status;
	    
	    @Column(name = "submitted_at")
	    private LocalDateTime submitted_at;
	    
	    @Column(name = "week_start")
	    private LocalDate weekStart;
	    
	    @Column(name = "week_end")
	    private LocalDate weekEnd;
	    
	    
	    
 
	    
	    
	    
 
		public String getEmpName() {
			return empName;
		}
 
 
 
 
		public void setEmpName(String empName) {
			this.empName = empName;
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
 
 
 
 
		@Override
		public String toString() {
			return "Timesheet [id=" + id + ", timesheetId=" + timesheetId + ", empId=" + empId + ", empName=" + empName
					+ ", projectId=" + projectId + ", taskId=" + taskId + ", projectName=" + projectName + ", taskName="
					+ taskName + ", timeCategory=" + timeCategory + ", resourcePlan=" + resourcePlan + ", mondayHours="
					+ mondayHours + ", tuesdayHours=" + tuesdayHours + ", wednesdayHours=" + wednesdayHours
					+ ", thursadyHours=" + thursadyHours + ", fridayHours=" + fridayHours + ", saturdayHours="
					+ saturdayHours + ", sundayHours=" + sundayHours + ", comments=" + comments + ", createdAt="
					+ createdAt + ", updatedAt=" + updatedAt + ", status=" + status + ", submitted_at=" + submitted_at
					+ ", weekStart=" + weekStart + ", weekEnd=" + weekEnd + "]";
		}
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	   
	   
 
}