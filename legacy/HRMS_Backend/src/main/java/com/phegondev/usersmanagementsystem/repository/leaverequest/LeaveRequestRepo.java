
package com.phegondev.usersmanagementsystem.repository.leaverequest;

import com.phegondev.usersmanagementsystem.entity.LeaveRequests;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepo  extends JpaRepository<LeaveRequests, Long> {

    List<LeaveRequests> findByEmployeeId(String employeeId);

    List<LeaveRequests> findByEmployeeIdIn(List<String> employeeIds);

    //  List<LeaveRequests> findByEmployeeNameContainingIgnoreCase(String employeeName);

    //  List<LeaveRequests> findByManualDaysAllocationContainingKey(Long leaveTypeId);

    //   List<LeaveRequests> findByEmployeeNameContainingIgnoreCaseAndManualDaysAllocationContainingKey(
    //           String employeeName, Long leaveTypeId
    //  );


}