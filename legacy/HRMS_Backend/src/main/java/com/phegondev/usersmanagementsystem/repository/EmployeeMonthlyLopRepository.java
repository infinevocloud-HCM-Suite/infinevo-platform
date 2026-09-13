package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.EmployeeMonthlyLop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface EmployeeMonthlyLopRepository
        extends JpaRepository<EmployeeMonthlyLop, Long> {

    Optional<EmployeeMonthlyLop>
    findByEmployeeIdAndLeaveTypeIdAndYearAndMonth(
            String employeeId,
            Long leaveTypeId,
            Integer year,
            Integer month);

    List<EmployeeMonthlyLop>
    findByEmployeeIdAndYearAndMonth(
            String employeeId,
            Integer year,
            Integer month);

    List<EmployeeMonthlyLop>
    findByLeaveRequestId(Long leaveRequestId);

    void deleteByLeaveRequestId(Long leaveRequestId);
}
