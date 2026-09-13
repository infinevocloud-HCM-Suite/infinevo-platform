package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_leave_balances")
public class EmployeeLeaveBalance {
	
	   @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    @Column(name = "employee_id", nullable = false)
	    private String employeeId;

	    @Column(name = "leave_type", nullable = false)
	    private String leaveType; // match with LeaveRequest.leaveType
	    

	    @Column(name = "leave_type_id", nullable = false)
	    private Long leaveTypeId;

	    @Column(name = "remaining_days", nullable = false)
	    private Float remainingDays;

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

		public String getLeaveType() {
			return leaveType;
		}

		public void setLeaveType(String leaveType) {
			this.leaveType = leaveType;
		}

		public Long getLeaveTypeId() {
			return leaveTypeId;
		}

		public void setLeaveTypeId(Long leaveTypeId) {
			this.leaveTypeId = leaveTypeId;
		}

		public Float getRemainingDays() {
			return remainingDays;
		}

		public void setRemainingDays(Float remainingDays) {
			this.remainingDays = remainingDays;
		}

		
		public EmployeeLeaveBalance() {
			super();
			// TODO Auto-generated constructor stub
		}
		
		

		public EmployeeLeaveBalance(Long id, String employeeId, String leaveType, Long leaveTypeId,
				Float remainingDays) {
			super();
			this.id = id;
			this.employeeId = employeeId;
			this.leaveType = leaveType;
			this.leaveTypeId = leaveTypeId;
			this.remainingDays = remainingDays;
		}

		@Override
		public String toString() {
			return "EmployeeLeaveBalance [id=" + id + ", employeeId=" + employeeId + ", leaveType=" + leaveType
					+ ", leaveTypeId=" + leaveTypeId + ", remainingDays=" + remainingDays + "]";
		}
	    
	    

}
