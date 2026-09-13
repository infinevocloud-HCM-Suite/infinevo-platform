package com.phegondev.usersmanagementsystem.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestDTO {
	
	    private Long id;
	    private String employeeId;
	    private String employeeName;
	    private String leaveType; // Should hold LeaveType ID or name consistently
	    private LocalDate fromDate;
	    private LocalDate toDate;
	    private String reason;
	    private String status;
	    private LocalDateTime createdAt;
	    private Long leaveTypeId;
	    
		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
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
		public String getLeaveType() {
			return leaveType;
		}
		public void setLeaveType(String leaveType) {
			this.leaveType = leaveType;
		}
		public LocalDate getFromDate() {
			return fromDate;
		}
		public void setFromDate(LocalDate fromDate) {
			this.fromDate = fromDate;
		}
		public LocalDate getToDate() {
			return toDate;
		}
		public void setToDate(LocalDate toDate) {
			this.toDate = toDate;
		}
		public String getReason() {
			return reason;
		}
		public void setReason(String reason) {
			this.reason = reason;
		}
		public String getStatus() {
			return status;
		}
		public void setStatus(String status) {
			this.status = status;
		}
		public LocalDateTime getCreatedAt() {
			return createdAt;
		}
		public void setCreatedAt(LocalDateTime createdAt) {
			this.createdAt = createdAt;
		}
		public Long getLeaveTypeId() {
			return leaveTypeId;
		}
		public void setLeaveTypeId(Long leaveTypeId) {
			this.leaveTypeId = leaveTypeId;
		}
		
		@Override
		public String toString() {
			return "LeaveRequestDTO [id=" + id + ", employeeId=" + employeeId + ", employeeName=" + employeeName
					+ ", leaveType=" + leaveType + ", fromDate=" + fromDate + ", toDate=" + toDate + ", reason="
					+ reason + ", status=" + status + ", createdAt=" + createdAt + ", leaveTypeId=" + leaveTypeId + "]";
		}
	    
	    

}
