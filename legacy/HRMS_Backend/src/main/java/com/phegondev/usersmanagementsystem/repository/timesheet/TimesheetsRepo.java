package com.phegondev.usersmanagementsystem.repository.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.phegondev.usersmanagementsystem.entity.timesheet.Timesheets;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

public interface TimesheetsRepo extends JpaRepository<Timesheets, Long> {

  // @Query("SELECT t FROM Timesheet t ORDER BY t.id DESC LIMIT 1")
  // Timesheets findLatestTimesheet();

  Timesheets findFirstByOrderByIdDesc();

  Optional<Timesheets> findByTimesheetId(String timesheetId);

  List<Timesheets> findByEmployeeIdAndCreatedAtBetween(String empId, LocalDateTime start, LocalDateTime end);

  List<Timesheets> findByEmployeeIdAndStatus(String employeeId, TimesheetStatus status);

  List<Timesheets> findByEmployeeIdAndWeekStartDateBetween(
      String employeeId, LocalDate weekStart, LocalDate weekEnd);

  List<Timesheets> findByEmployeeId(String empId);

  List<Timesheets> findByProjectsProjectIdIn(List<Long> projectIds);

  List<Timesheets> findByProjectsProjectIdInAndWeekStartDateBetween(List<Long> projectIds, LocalDate startDate,
      LocalDate endDate);

  List<Timesheets> findByStatusNot(TimesheetStatus status);

  List<Timesheets> findByEmployeeIdInAndStatusNot(List<String> employeeIds, TimesheetStatus status);

  List<Timesheets> findByStatusAndWeekStartDateBetween(
          TimesheetStatus status,
          LocalDate weekStart,
          LocalDate weekEnd
          
          
  );
  
  @Query("SELECT DISTINCT t.employeeId FROM Timesheets t WHERE t.status = 'DRAFT'")
  List<String> findEmployeeIdsWithDraftTimesheets();
  
  boolean existsByEmployeeIdAndWeekStartDateBetween(String employeeId, LocalDate start, LocalDate end);

  List<Timesheets> findByEmployeeIdInAndStatusNotIn(List<String> employeeIds, List<TimesheetStatus> statuses);

}
