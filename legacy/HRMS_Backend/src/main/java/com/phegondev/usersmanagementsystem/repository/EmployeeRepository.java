package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.Employee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByPersonalEmpId(String empId);
    boolean existsByPersonalEmpId(String empId);
    
    @Query("SELECT e.personal.empId FROM Employee e")
    List<String> findAllEmployeeIds();

    List<Employee> findByReport_ReportingManagerId(String reportingManagerId);
}