package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByEmployeeIdAndYear(String employeeId, int year);
    List<LeaveBalance> findByEmployeeId(String employeeId);
    List<LeaveBalance> findByYear(int year);
    
    @Query("SELECT lb FROM LeaveBalance lb WHERE " +
           "(:year IS NULL OR lb.year = :year) AND " +
           "(:employeeId IS NULL OR lb.employeeId = :employeeId)")
    List<LeaveBalance> findByYearAndEmployeeId(@Param("year") Integer year, 
                                             @Param("employeeId") String employeeId);
}