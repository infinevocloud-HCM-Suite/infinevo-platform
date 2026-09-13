package com.phegondev.usersmanagementsystem.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.phegondev.usersmanagementsystem.entity.Timesheet;

public interface TimesheetRepo extends JpaRepository<Timesheet, Long> {

	@Query("SELECT t FROM Timesheet t ORDER BY t.id DESC LIMIT 1")
	Timesheet findLatestTimesheet();

	boolean existsByTimesheetIdAndTaskId(String timesheetId, Long taskId);
	Optional<Timesheet> findByTimesheetId(String timesheetId);
	List<Timesheet> findByEmpId(String empId);
	List<Timesheet> findByEmpIdAndCreatedAtBetween(String empId, LocalDateTime start, LocalDateTime end);
	List<Timesheet> findByEmpIdAndStatus(String empId, String status);
	List<Timesheet> findByStatus(String status);
	List<Timesheet> findByStatusNot(String status);
	List<Timesheet> findByWeekStartAndStatusNot(LocalDate weekStart, String status);
	List<Timesheet> findByStatusAndWeekStart(String status, LocalDate weekStart);



	// @Query("SELECT t FROM Timesheet t WHERE t.status = 'Submitted'")
	// List<Timesheet> findAllSubmittedTimesheets();

}