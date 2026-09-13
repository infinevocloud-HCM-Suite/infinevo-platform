package com.phegondev.usersmanagementsystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.phegondev.usersmanagementsystem.entity.Employee;
import com.phegondev.usersmanagementsystem.entity.EmployeeLeaveBalance;

public interface EmployeeLeaveBalanceRepo extends JpaRepository<EmployeeLeaveBalance, Long> {

    Optional<EmployeeLeaveBalance> findByEmployeeIdAndLeaveTypeId(String employeeId, Long leaveTypeId);
    
    List<EmployeeLeaveBalance> findByEmployeeId(String employeeId);

}
