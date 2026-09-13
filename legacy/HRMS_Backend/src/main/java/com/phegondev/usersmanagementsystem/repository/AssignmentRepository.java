package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.dto.ProjectNameIdDTO;
import com.phegondev.usersmanagementsystem.dto.timesheet.ProjectEntryDto;
import com.phegondev.usersmanagementsystem.entity.Assignment;
import com.phegondev.usersmanagementsystem.entity.Employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByProjectId(Long projectId);
    
    List<Assignment> findByEmpId(String empId);
    
    
    // @Query("SELECT new com.phegondev.usersmanagementsystem.dto.ProjectNameIdDTO( a.project.id, a.project.name) " +
    //         "FROM Assignment a WHERE a.empId = :empId")
    //  List<ProjectNameIdDTO> findProjectIdAndNameByEmpId(@Param("empId") String empId);

     @Query("SELECT new com.phegondev.usersmanagementsystem.dto.timesheet.ProjectEntryDto( a.project.id, a.project.name) " +
            "FROM Assignment a WHERE a.empId = :empId")
     List<ProjectEntryDto> findProjectIdAndNameByEmpId(@Param("empId") String empId);

}