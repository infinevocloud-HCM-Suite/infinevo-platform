package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto;
import com.phegondev.usersmanagementsystem.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    List<Task> findByStatus(String status);
    
    @Query("SELECT t FROM Task t JOIN t.project p JOIN p.assignments a WHERE a.empId = :empId")
    List<Task> findTasksByAssignedEmpId(@Param("empId") String empId);
    
    @Query("SELECT new com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto(t.id, t.title) " +
           "FROM Task t WHERE t.project.id = :projectId")
     List<TaskEntryDto> findTaskIdAndTitleByProjectId(@Param("projectId") Long projectId);

         // ✅ NEW: Fetch tasks for all project IDs belonging to a manager
    List<Task> findByProjectIdIn(List<Long> projectIds);

}