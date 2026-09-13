package com.phegondev.usersmanagementsystem.service.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;

import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetInitialDataDto;
import com.phegondev.usersmanagementsystem.entity.timesheet.Timesheets;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

public interface TimesheetService {

	void saveTimesheet(TimesheetDto dto);

	TimesheetInitialDataDto getInitialData(String empId);

	List<TaskEntryDto> getTasksByProjectId(Long projectId);

	TimesheetDto getTimesheetById(String timesheetId);

	// List<TimesheetDto> getCurrentWeekTimesheets(String empId);
	TimesheetDto updateTimesheet(String timesheetId, TimesheetDto dto);

	void deleteTimesheetByTimesheetId(String timesheetId);

	boolean isValidForSubmission(Timesheets timesheet);

	List<Timesheets> findByEmployeeIdAndStatus(String empId, TimesheetStatus status);

	List<Timesheets> updateTimesheetsStatus(List<Timesheets> timesheets, TimesheetStatus newStatus);

	List<TimesheetDto> getFilteredTimesheets(String empId, LocalDate startDate, LocalDate endDate, String projectName,
			TimesheetStatus status);

	List<TimesheetDto> getTimesheetsByEmployeeAndWeek(String empId, LocalDate weekStart, LocalDate weekEnd);

	Optional<Timesheets> findByTimesheetId(String timesheetId);

	Timesheets updateTimesheetStatus(Timesheets timesheet, TimesheetStatus newStatus);

	List<TimesheetDto> getTimesheetsByProjectIds(List<Long> projectIds, LocalDate startDate, LocalDate endDate,
			TimesheetStatus status);

	TimesheetStatus updateProjectAndTimesheetStatus(
			String timesheetId,
			Long projectId,
			TimesheetStatus newProjectStatus,
			String rejectionReason);

	List<TimesheetDto> getAllTimesheetsExcludingDrafts();

	List<TimesheetDto> getTimesheetsByReportingManager(String reportingManagerId);

	TimesheetDto getTimesheetByTimesheetIdAndProjectId(String timesheetId, Long projectId);

	List<UserDTO> getUsersWithPendingTimesheetsForCurrentWeek();

	boolean cancelTimesheet(String timesheetId);
	
	

}
