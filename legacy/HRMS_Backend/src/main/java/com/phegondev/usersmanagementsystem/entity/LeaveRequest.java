//package com.phegondev.usersmanagementsystem.entity;

//import jakarta.persistence.*;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//
//import java.time.LocalDate;
//
//
//@Entity
////@Data
////@Getter
////@Setter
////@NoArgsConstructor
////@AllArgsConstructor
//public class LeaveRequest {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private String empId;
//    private String name;
//    private String leaveType;
//    private LocalDate fromDate;
//    private LocalDate toDate;
//    private String reason;
//	public Long getId() {
//		return id;
//	}
//	public void setId(Long id) {
//		this.id = id;
//	}
//	public String getEmpId() {
//		return empId;
//	}
//	public void setEmpId(String empId) {
//		this.empId = empId;
//	}
//	public String getName() {
//		return name;
//	}
//	public void setName(String name) {
//		this.name = name;
//	}
//	public String getLeaveType() {
//		return leaveType;
//	}
//	public void setLeaveType(String leaveType) {
//		this.leaveType = leaveType;
//	}
//	public LocalDate getFromDate() {
//		return fromDate;
//	}
//	public void setFromDate(LocalDate fromDate) {
//		this.fromDate = fromDate;
//	}
//	public LocalDate getToDate() {
//		return toDate;
//	}
//	public void setToDate(LocalDate toDate) {
//		this.toDate = toDate;
//	}
//	public String getReason() {
//		return reason;
//	}
//	public void setReason(String reason) {
//		this.reason = reason;
//	}
package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
//@Table(name = "leave_requests")
@Table(name = "leave_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "leave_type", nullable = false)
    private String leaveType;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;


    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(columnDefinition = "TEXT", name = "reason", nullable = false)
    private String reason;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Optional: convenience method if you want to access "start date" conceptually
    public LocalDate getStartDate() {
        return fromDate;
    }
}
