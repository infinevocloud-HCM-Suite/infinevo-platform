//package com.phegondev.usersmanagementsystem.repository;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import com.phegondev.usersmanagementsystem.entity.LeaveRequest;
//
//
//
//@Repository
//public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
//}


package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // Get leave requests by status
    List<LeaveRequest> findByStatus(String status);

    // Get all leave requests sorted by createdAt DESC
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    // Search by employee name (case insensitive)
    List<LeaveRequest> findByEmployeeNameIgnoreCaseOrderByCreatedAtDesc(String employeeName);

    // Search by leave type (case insensitive)
    List<LeaveRequest> findByLeaveTypeIgnoreCaseOrderByCreatedAtDesc(String leaveType);

    // Combined filter: employee name + leave type
    List<LeaveRequest> findByEmployeeNameIgnoreCaseAndLeaveTypeIgnoreCaseOrderByCreatedAtDesc(String employeeName, String leaveType);
    
    List<LeaveRequest> findByEmployeeId(String employeeId);
}