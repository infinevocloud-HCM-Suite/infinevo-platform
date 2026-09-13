package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByName(String name);
 //   List<LeaveType> findAllByIsActive(boolean isActive);
    List<LeaveType> findAllByOrderByCreatedAtDesc();
    List<LeaveType> findAllByOrderByNameAsc();
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
//    List<LeaveType> findByEmployeeId(String employeeId);
    
   

        // Find leave types that apply to all OR contain the employeeId in employeeIds
        @Query("SELECT lt FROM LeaveType lt " +
               "WHERE lt.applyToAllEmployees = true " +
               "OR :employeeId IN elements(lt.employeeIds)")
        List<LeaveType> findApplicableLeaveTypesForEmployee(@Param("employeeId") String employeeId);
    

}