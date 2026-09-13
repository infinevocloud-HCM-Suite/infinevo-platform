package com.phegondev.usersmanagementsystem.serviceimpl.timeshhet;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import com.phegondev.usersmanagementsystem.entity.Assignment;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.service.MailService;
import jakarta.mail.MessagingException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.dto.timesheet.DayEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.ProjectEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetInitialDataDto;
import com.phegondev.usersmanagementsystem.repository.AssignmentRepository;
import com.phegondev.usersmanagementsystem.repository.TaskRepository;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.timesheet.TimesheetsRepo;
import com.phegondev.usersmanagementsystem.service.EmployeeService;
import com.phegondev.usersmanagementsystem.service.ProjectService;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetService;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetsNotificationService;
import com.phegondev.usersmanagementsystem.entity.timesheet.Timesheets;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.entity.timesheet.DayEntry;
import com.phegondev.usersmanagementsystem.entity.timesheet.ProjectEntry;
import com.phegondev.usersmanagementsystem.entity.timesheet.TaskEntry;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;

@Service
public class TimesheetServiceImpl implements TimesheetService {

	private final TimesheetsRepo timesheetRepo;
	private final AssignmentRepository assignmentRepository;
	private final TaskRepository taskRepository;
	private final ProjectService projectService;
	private final TimesheetsNotificationService notificationService;
	private final EmployeeService employeeService;
	private final MailService mailService;
	 private final UsersRepo usersRepo;

	public TimesheetServiceImpl(TimesheetsRepo timesheetRepo, AssignmentRepository assignmentRepository,
			TaskRepository taskRepository, UsersRepo usersRepo,
			ProjectService projectService,
			EmployeeService employeeService,
								MailService mailService,
			TimesheetsNotificationService notificationService) {
		this.timesheetRepo = timesheetRepo;
		this.assignmentRepository = assignmentRepository;
		this.taskRepository = taskRepository;
		this.projectService = projectService;
		this.notificationService = notificationService;
		this.employeeService = employeeService;
		this.mailService = mailService;
		this.usersRepo = usersRepo;
	}

	// Saving a timesheet with all its projects, tasks, and day entries
	@Override
	public void saveTimesheet(TimesheetDto dto) {

		System.out.println("=== Starting saveTimesheet ===");
		System.out.println("Received TimesheetDto: " + dto);

		Timesheets timesheet = new Timesheets();

		timesheet.setWeekStartDate(dto.getWeekStartDate());
		timesheet.setWeekEndDate(dto.getWeekEndDate());
		timesheet.setEmployeeId(dto.getEmployeeId());
		timesheet.setEmployeeName(dto.getEmployeeName());
		timesheet.setSubmitted_at(dto.getSubmitted_at());

		System.out.println("Mapped basic Timesheets entity: " + timesheet);

		List<ProjectEntry> projectEntries = dto.getProjects().stream().map(projectDto -> {
			System.out.println("Mapping ProjectEntryDto: " + projectDto);

			ProjectEntry project = new ProjectEntry();
			project.setProjectId(projectDto.getProjectId());
			project.setProjectName(projectDto.getProjectName());
			project.setTimesheet(timesheet); // back reference

			List<TaskEntry> tasks = projectDto.getTasks().stream().map(taskDto -> {
				System.out.println("  Mapping TaskEntryDto: " + taskDto);

				TaskEntry task = new TaskEntry();
				task.setTaskId(taskDto.getTaskId());
				task.setTaskName(taskDto.getTaskName());
				task.setProjectEntry(project); // back reference

				List<DayEntry> dayEntries = taskDto.getDays().stream().map(dayDto -> {
					System.out.println("    Mapping DayEntryDto: " + dayDto);

					DayEntry day = new DayEntry();
					day.setDate(dayDto.getDate());
					day.setDayName(dayDto.getDayName());
					day.setHours(dayDto.getHours());
					day.setDescription(dayDto.getDescription());
					day.setTaskEntry(task); // back reference

					return day;
				}).collect(Collectors.toList());

				task.setDays(dayEntries);
				return task;
			}).collect(Collectors.toList());

			project.setTasks(tasks);
			return project;
		}).collect(Collectors.toList());

		timesheet.setProjects(projectEntries);

		System.out.println("Saving Timesheet entity with full hierarchy...");
		Timesheets saved = timesheetRepo.save(timesheet);

		String formattedId = "TS-" + (100 + saved.getId());
		saved.setTimesheetId(formattedId);

		timesheetRepo.save(saved);

		System.out.println("✅ Timesheet saved with ID: " + formattedId);

		System.out.println("=== Timesheet saved successfully ===");
	}

	// Fetching initial data for the timesheet creation
	@Override
	public TimesheetInitialDataDto getInitialData(String empId) {

		System.out.println("📥 [Service] getInitialData() called with empId: " + empId);

		List<ProjectEntryDto> projectNameIds = assignmentRepository.findProjectIdAndNameByEmpId(empId);
		System.out.println("📦 [Service] Projects retrieved for empId " + empId + ":");

		for (ProjectEntryDto dto : projectNameIds) {
			System.out.println("➡️ Project ID: " + dto.getProjectId() + ", Project Name: " + dto.getProjectName());
		}

		TimesheetInitialDataDto responseDto = new TimesheetInitialDataDto(empId, projectNameIds);
		System.out.println("✅ [Service] Returning TimesheetInitialDataDTO: " + responseDto);

		return responseDto;
	}

	// Fetching tasks by project ID
	@Override
	public List<TaskEntryDto> getTasksByProjectId(Long projectId) {
		System.out.println("📥 Fetching task list for projectId: " + projectId);
		List<TaskEntryDto> tasks = taskRepository.findTaskIdAndTitleByProjectId(projectId);
		tasks.forEach(task -> System.out.println("➡️ Task: " + task));
		return tasks;
	}

	// Fetching a timesheet by its ID
	@Override
	public TimesheetDto getTimesheetById(String timesheetId) {
		System.out.println("Fetching Timesheet with ID: " + timesheetId);

		Optional<Timesheets> optionalTimesheet = timesheetRepo.findByTimesheetId(timesheetId);

		if (optionalTimesheet.isEmpty()) {
			System.out.println("Timesheet not found");
			return null;
		}

		Timesheets entity = optionalTimesheet.get();
		TimesheetDto dto = new TimesheetDto();

		dto.setTimesheetId(entity.getTimesheetId());
		dto.setWeekStartDate(entity.getWeekStartDate());
		dto.setWeekEndDate(entity.getWeekEndDate());
		dto.setStatus(entity.getStatus());
		dto.setEmployeeId(entity.getEmployeeId());
		dto.setEmployeeName(entity.getEmployeeName());
		dto.setSubmitted_at(entity.getSubmitted_at());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setUpdatedAt(entity.getUpdatedAt());

		List<ProjectEntryDto> projectDtos = entity.getProjects().stream().map(project -> {
			ProjectEntryDto projectDto = new ProjectEntryDto();
			projectDto.setProjectId(project.getProjectId());
			projectDto.setProjectName(project.getProjectName());
			projectDto.setStatus(project.getStatus());

			List<TaskEntryDto> taskDtos = project.getTasks().stream().map(task -> {
				TaskEntryDto taskDto = new TaskEntryDto();
				taskDto.setTaskId(task.getTaskId());
				taskDto.setTaskName(task.getTaskName());

				List<DayEntryDto> dayDtos = task.getDays().stream().map(day -> {
					DayEntryDto dayDto = new DayEntryDto();
					dayDto.setDate(day.getDate());
					dayDto.setDayName(day.getDayName());
					dayDto.setHours(day.getHours());
					dayDto.setDescription(day.getDescription());
					return dayDto;
				}).collect(Collectors.toList());

				taskDto.setDays(dayDtos);
				return taskDto;
			}).collect(Collectors.toList());

			projectDto.setTasks(taskDtos);
			return projectDto;
		}).collect(Collectors.toList());

		dto.setProjects(projectDtos);

		System.out.println("Timesheet fetched successfully");
		return dto;
	}

	// Mapping Timesheet entity to DTO
	public TimesheetDto mapToDTO(Timesheets timesheet) {
		TimesheetDto dto = new TimesheetDto();
		dto.setTimesheetId(timesheet.getTimesheetId());
		dto.setWeekStartDate(timesheet.getWeekStartDate());
		dto.setWeekEndDate(timesheet.getWeekEndDate());
		dto.setStatus(timesheet.getStatus());
		dto.setEmployeeId(timesheet.getEmployeeId());
		dto.setEmployeeName(timesheet.getEmployeeName());
		dto.setSubmitted_at(timesheet.getSubmitted_at());
		dto.setCreatedAt(timesheet.getCreatedAt());
		dto.setUpdatedAt(timesheet.getUpdatedAt());
		List<ProjectEntryDto> projectDtos = timesheet.getProjects().stream().map(project -> {
			ProjectEntryDto projectDto = new ProjectEntryDto();
			projectDto.setProjectId(project.getProjectId());
			projectDto.setProjectName(project.getProjectName());
			projectDto.setStatus(project.getStatus());
			projectDto.setRejectionReason(project.getRejectionReason());

			List<TaskEntryDto> taskDtos = project.getTasks().stream().map(task -> {
				TaskEntryDto taskDto = new TaskEntryDto();
				taskDto.setTaskId(task.getTaskId());
				taskDto.setTaskName(task.getTaskName());

				List<DayEntryDto> dayDtos = task.getDays().stream().map(day -> {
					DayEntryDto dayDto = new DayEntryDto();
					dayDto.setDate(day.getDate());
					dayDto.setDayName(day.getDayName());
					dayDto.setHours(day.getHours());
					dayDto.setDescription(day.getDescription());
					return dayDto;
				}).collect(Collectors.toList());

				taskDto.setDays(dayDtos);
				return taskDto;
			}).collect(Collectors.toList());

			projectDto.setTasks(taskDtos);
			return projectDto;
		}).collect(Collectors.toList());
		dto.setProjects(projectDtos);
		return dto;
	}

	// Fetching timesheets for the current week
	@Override
	public List<TimesheetDto> getTimesheetsByEmployeeAndWeek(String empId, LocalDate weekStart, LocalDate weekEnd) {
		System.out.println("Fetching timesheets for employee " + empId +
				" between " + weekStart + " and " + weekEnd);

		LocalDateTime startDateTime = weekStart.atStartOfDay();
		LocalDateTime endDateTime = weekEnd.atTime(LocalTime.MAX);

		List<Timesheets> timesheets = timesheetRepo.findByEmployeeIdAndWeekStartDateBetween(
				empId, weekStart, weekEnd);

		return timesheets.stream()
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}

	@Override
	public List<TimesheetDto> getFilteredTimesheets(String empId, LocalDate startDate, LocalDate endDate,
			String projectName, TimesheetStatus status) {
		System.out.println("Fetching filtered timesheets for employee " + empId);

		List<Timesheets> timesheets;

		if (startDate != null && endDate != null) {
			// If date range is provided
			timesheets = timesheetRepo.findByEmployeeIdAndWeekStartDateBetween(empId, startDate, endDate);
		} else {
			// If no date range, get all timesheets for employee
			timesheets = timesheetRepo.findByEmployeeId(empId);
		}

		// Apply additional filters
		return timesheets.stream()
				.filter(t -> status == null || t.getStatus() == status)
				.filter(t -> projectName == null ||
						t.getProjects().stream()
								.anyMatch(p -> p.getProjectName().equals(projectName)))
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}

	// Deleting a timesheet by its ID
	@Override
	public void deleteTimesheetByTimesheetId(String timesheetId) {
		System.out.println("Attempting to delete timesheet with ID: " + timesheetId);
		Timesheets timesheet = timesheetRepo.findByTimesheetId(timesheetId)
				.orElseThrow(() -> new RuntimeException("Timesheet not found with ID: " + timesheetId));
		timesheetRepo.delete(timesheet);
		System.out.println("Successfully deleted timesheet with ID: " + timesheetId);
	}

	@Override
	@Transactional
	public boolean cancelTimesheet(String timesheetId) {
		Optional<Timesheets> optionalTimesheet = timesheetRepo.findByTimesheetId(timesheetId);
		if (optionalTimesheet.isPresent()) {
			Timesheets timesheet = optionalTimesheet.get();
			timesheet.setStatus(TimesheetStatus.CANCELLED);

			// Assuming timesheet has a list of projects mapped
			if (timesheet.getProjects() != null) {
				timesheet.getProjects().forEach(project -> project.setStatus(TimesheetStatus.CANCELLED));
			}

			timesheetRepo.save(timesheet);
			return true;
		}
		return false;
	}

	// Submit a single timesheet
	@Override
	public Optional<Timesheets> findByTimesheetId(String timesheetId) {
		return timesheetRepo.findByTimesheetId(timesheetId);
	}

	@Override
	@Transactional
	public Timesheets updateTimesheetStatus(Timesheets timesheet, TimesheetStatus newStatus) {
		LocalDateTime now = LocalDateTime.now();

		timesheet.setStatus(newStatus);
		if (newStatus == TimesheetStatus.SUBMITTED) {
			timesheet.setSubmitted_at(now);
			// Notify manager on submission
			notifyManagerOnSubmission(timesheet);
		}
		timesheet.setUpdatedAt(now);

		// Update all projects to SUBMITTED status as well
		timesheet.getProjects().forEach(project -> {
			project.setStatus(newStatus);
		});

		return timesheetRepo.save(timesheet);
	}

	// Submit all timesheets whose timesheet and all project statuses are DRAFT
	// Check if the timesheet is valid for submission
	@Override
	public boolean isValidForSubmission(Timesheets timesheet) {
		// Check if timesheet is in DRAFT status
		if (!timesheet.getStatus().equals(TimesheetStatus.DRAFT)) {
			return false;
		}

		// Check all projects are in DRAFT status
		return timesheet.getProjects().stream()
				.allMatch(project -> project.getStatus() != null &&
						project.getStatus().equals(TimesheetStatus.DRAFT));
	}

	@Override
	public List<Timesheets> findByEmployeeIdAndStatus(String empId, TimesheetStatus status) {
		return timesheetRepo.findByEmployeeIdAndStatus(empId, status);
	}

	@Override
	@Transactional
	public List<Timesheets> updateTimesheetsStatus(List<Timesheets> timesheets, TimesheetStatus newStatus) {
		LocalDateTime now = LocalDateTime.now();

		timesheets.forEach(t -> {
			t.setStatus(newStatus);
			if (newStatus == TimesheetStatus.SUBMITTED) {
				t.setSubmitted_at(now);
				// Notify all project managers for each project in each timesheet
				notifyManagerOnSubmission(t);
			}
			t.setUpdatedAt(now);

			// Update all projects to SUBMITTED status as well
			t.getProjects().forEach(project -> {
				project.setStatus(newStatus);
			});
		});

		return timesheetRepo.saveAll(timesheets);
	}

	// Fetch the timesheets of employee on the basis of their project's project
	// manager
	@Override
	public List<TimesheetDto> getTimesheetsByProjectIds(List<Long> projectIds, LocalDate startDate, LocalDate endDate,
			TimesheetStatus status) {
		System.out.println("Fetching timesheets for project IDs: " + projectIds);

		List<Timesheets> timesheets;

		// Step 1: Fetch timesheets by project IDs and optional date range
		if (startDate != null && endDate != null) {
			timesheets = timesheetRepo.findByProjectsProjectIdInAndWeekStartDateBetween(projectIds, startDate, endDate);
		} else {
			timesheets = timesheetRepo.findByProjectsProjectIdIn(projectIds);
		}

		// Step 2: Exclude DRAFT and CANCELLED timesheets
		timesheets = timesheets.stream()
				.filter(t -> t.getStatus() != TimesheetStatus.DRAFT  && t.getStatus() != TimesheetStatus.CANCELLED)
				.collect(Collectors.toList());

		// Step 3: Map to DTO and filter projects within each timesheet
		return timesheets.stream()
				.map(t -> {
					TimesheetDto dto = mapToDTO(t);
					// Filter projects to only include those managed by this manager
					List<ProjectEntryDto> filteredProjects = dto.getProjects().stream()
							.filter(project -> projectIds.contains(project.getProjectId()))
							.collect(Collectors.toList());
					dto.setProjects(filteredProjects);
					return dto;
				})
				.filter(dto -> !dto.getProjects().isEmpty()) // Only include timesheets with at least one project for
																// this manager
				.collect(Collectors.toList());
	}

	// Updating the timesheet and project status after the approving or rejecting a
	// projects of the timesheet
	@Override
	@Transactional
	public TimesheetStatus updateProjectAndTimesheetStatus(
			String timesheetId,
			Long projectId,
			TimesheetStatus newProjectStatus,
			String rejectionReason) {

		// Find the timesheet
		Timesheets timesheet = timesheetRepo.findByTimesheetId(timesheetId)
				.orElseThrow(() -> new RuntimeException("Timesheet not found"));

		// Find and update the specific project
		ProjectEntry project = timesheet.getProjects().stream()
				.filter(p -> p.getProjectId().equals(projectId))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Project not found in timesheet"));

		// Update project status
		project.setStatus(newProjectStatus);

		// Handle rejection reason
		if (newProjectStatus == TimesheetStatus.REJECTED) {
			project.setRejectionReason(rejectionReason);
		} else {
			project.setRejectionReason(null); // Clear if not rejected
		}

		// Notify employee of status change
		notifyEmployeeOnStatusChange(timesheet, projectId, newProjectStatus, rejectionReason);

		// Determine new timesheet status based on all projects
		TimesheetStatus calculatedStatus = calculateTimesheetStatus(timesheet);

		// Update timesheet status if changed
		if (calculatedStatus != timesheet.getStatus()) {
			timesheet.setStatus(calculatedStatus);
			timesheetRepo.save(timesheet);
		}

		return calculatedStatus;
	}

	private TimesheetStatus calculateTimesheetStatus(Timesheets timesheet) {
		boolean allApproved = true;
		boolean anyRejected = false;
		boolean anySubmitted = false;

		for (ProjectEntry project : timesheet.getProjects()) {
			if (project.getStatus() == TimesheetStatus.REJECTED) {
				anyRejected = true;
				allApproved = false;
			} else if (project.getStatus() == TimesheetStatus.SUBMITTED) {
				anySubmitted = true;
				allApproved = false;
			} else if (project.getStatus() != TimesheetStatus.APPROVED) {
				allApproved = false;
			}
		}

		if (anyRejected) {
			return TimesheetStatus.REJECTED;
		} else if (anySubmitted) {
			return TimesheetStatus.SUBMITTED;
		} else if (allApproved) {
			return TimesheetStatus.APPROVED;
		}
		return timesheet.getStatus(); // No change needed
	}

	// Updating timesheet on the basis of DRAFT or REJECTED status
	@Transactional
	@Override
	public TimesheetDto updateTimesheet(String timesheetId, TimesheetDto dto) {
		System.out.println("Starting updateTimesheet for ID: " + timesheetId);

		try {
			Optional<Timesheets> optionalTimesheet = timesheetRepo.findByTimesheetId(timesheetId);
			System.out.println("Fetched timesheet from DB: " + (optionalTimesheet.isPresent() ? "Found" : "Not Found"));

			if (optionalTimesheet.isEmpty()) {
				System.out.println("Timesheet not found, throwing exception");
				throw new RuntimeException("Timesheet not found with ID: " + timesheetId);
			}

			Timesheets existingTimesheet = optionalTimesheet.get();
			System.out.println("Existing timesheet status: " + existingTimesheet.getStatus());

			if (!existingTimesheet.getStatus().equals(TimesheetStatus.DRAFT)
					&& !existingTimesheet.getStatus().equals(TimesheetStatus.REJECTED)) {
				System.out.println("Invalid status for update, throwing exception");
				throw new RuntimeException("Only DRAFT or REJECTED timesheets can be updated");
			}

			if (existingTimesheet.getStatus().equals(TimesheetStatus.DRAFT)) {
				System.out.println("Updating DRAFT timesheet...");
				updateDraftTimesheet(existingTimesheet, dto);
			} else if (existingTimesheet.getStatus().equals(TimesheetStatus.REJECTED)) {
				System.out.println("Updating REJECTED timesheet...");
				updateRejectedTimesheet(existingTimesheet, dto);
			}

			Timesheets saved = timesheetRepo.save(existingTimesheet);
			System.out.println("Timesheet saved successfully with ID: " + saved.getId());

			TimesheetDto resultDto = mapToDTO(saved);
			System.out.println("Mapped updated timesheet to DTO");

			return resultDto;

		} catch (Exception e) {
			System.out.println("Exception occurred while updating timesheet: " + e.getMessage());
			throw new RuntimeException("Error updating timesheet: " + e.getMessage());
		}
	}

	private void updateDraftTimesheet(Timesheets timesheet, TimesheetDto dto) {
		System.out.println("Starting updateDraftTimesheet method");

		// Clear existing projects by removing them one by one
		// This maintains the Hibernate managed collection
		List<ProjectEntry> projectsToRemove = new ArrayList<>(timesheet.getProjects());
		for (ProjectEntry project : projectsToRemove) {
			timesheet.getProjects().remove(project);
		}

		// Add new projects
		for (ProjectEntryDto projectDto : dto.getProjects()) {
			System.out.println("Processing project DTO with ID: " + projectDto.getProjectId());

			ProjectEntry project = new ProjectEntry();
			project.setProjectId(projectDto.getProjectId());
			project.setProjectName(projectDto.getProjectName());
			project.setTimesheet(timesheet);
			System.out.println("Created new project entity with name: " + projectDto.getProjectName());

			List<TaskEntry> taskEntries = new ArrayList<>();
			for (TaskEntryDto taskDto : projectDto.getTasks()) {
				System.out.println("  Adding task with ID: " + taskDto.getTaskId());

				TaskEntry task = new TaskEntry();
				task.setTaskId(taskDto.getTaskId());
				task.setTaskName(taskDto.getTaskName());
				task.setProjectEntry(project);
				System.out.println("  Task name: " + taskDto.getTaskName());

				List<DayEntry> dayEntries = new ArrayList<>();
				for (DayEntryDto dayDto : taskDto.getDays()) {
					System.out.println("    Adding day entry for date: " + dayDto.getDate());

					DayEntry day = new DayEntry();
					day.setDate(dayDto.getDate());
					day.setDayName(dayDto.getDayName());
					day.setHours(dayDto.getHours());
					day.setDescription(dayDto.getDescription());
					day.setTaskEntry(task);

					System.out.println("    Day details - Date: " + dayDto.getDate() + ", Day: " + dayDto.getDayName() +
							", Hours: " + dayDto.getHours() + ", Description: " + dayDto.getDescription());

					dayEntries.add(day);
				}

				task.setDays(dayEntries);
				System.out.println("  Added " + dayEntries.size() + " day entries to task ID: " + task.getTaskId());
				taskEntries.add(task);
			}

			project.setTasks(taskEntries);
			System.out.println("Added " + taskEntries.size() + " tasks to project ID: " + project.getProjectId());
			timesheet.getProjects().add(project);
		}

		System.out.println(
				"Assigned " + timesheet.getProjects().size() + " projects to timesheet ID: " + timesheet.getId());
		System.out.println("Completed updateDraftTimesheet method");
	}

	private void updateRejectedTimesheet(Timesheets timesheet, TimesheetDto dto) {
		System.out.println("Starting updateRejectedTimesheet method");

		// Create a map of existing REJECTED projects
		Map<Long, ProjectEntry> existingRejectedProjects = new HashMap<>();
		for (ProjectEntry p : timesheet.getProjects()) {
			System.out.println("Checking project with ID: " + p.getProjectId() + ", Status: " + p.getStatus());
			if (p.getStatus().equals(TimesheetStatus.REJECTED)) {
				existingRejectedProjects.put(p.getProjectId(), p);
				System.out.println("Added to rejected project map: " + p.getProjectId());
			}
		}

		// Loop through each project from the DTO
		for (ProjectEntryDto projectDto : dto.getProjects()) {
			System.out.println("Processing projectDto with ID: " + projectDto.getProjectId());

			if (existingRejectedProjects.containsKey(projectDto.getProjectId())) {
				ProjectEntry existingProject = existingRejectedProjects.get(projectDto.getProjectId());
				System.out.println("Updating existing rejected project ID: " + existingProject.getProjectId());

				existingProject.setStatus(TimesheetStatus.SUBMITTED);
				System.out.println("project name : " + projectDto.getProjectName());

				// Clear existing tasks by removing them one by one
				List<TaskEntry> tasksToRemove = new ArrayList<>(existingProject.getTasks());
				for (TaskEntry task : tasksToRemove) {
					existingProject.getTasks().remove(task);
				}

				// Create new list of tasks
				for (TaskEntryDto taskDto : projectDto.getTasks()) {
					System.out.println("Adding task with ID: " + taskDto.getTaskId());

					TaskEntry task = new TaskEntry();
					task.setTaskId(taskDto.getTaskId());
					task.setTaskName(taskDto.getTaskName());
					task.setProjectEntry(existingProject);
					System.out.println("Task name: " + taskDto.getTaskName());

					// Create list of day entries for this task
					List<DayEntry> dayEntries = new ArrayList<>();
					for (DayEntryDto dayDto : taskDto.getDays()) {
						System.out.println("Adding day entry for date: " + dayDto.getDate());

						DayEntry day = new DayEntry();
						day.setDate(dayDto.getDate());
						day.setDayName(dayDto.getDayName());
						day.setHours(dayDto.getHours());
						day.setDescription(dayDto.getDescription());
						day.setTaskEntry(task);

						System.out.println("Day details - Date: " + dayDto.getDate() + ", Day: " + dayDto.getDayName() +
								", Hours: " + dayDto.getHours() + ", Description: " + dayDto.getDescription());

						dayEntries.add(day);
					}

					task.setDays(dayEntries);
					existingProject.getTasks().add(task);
					System.out.println("Added task with " + dayEntries.size() + " day entries");
				}

				System.out.println("Updated project ID " + existingProject.getProjectId() + " with "
						+ existingProject.getTasks().size() + " tasks");

                // Get manager and employee details
                String managerId = projectService.getProjectManagerId(existingProject.getProjectId());
                String managerEmail = projectService.getProjectManagerEmail(existingProject.getProjectId());
                String employeeEmail = employeeService.getEmployeeEmail(timesheet.getEmployeeId());

                // Create notification for manager
				String message = String.format(
						"Timesheet %s for project %s has been resubmitted by %s after rejection",
						timesheet.getTimesheetId(),
						existingProject.getProjectName(),
						timesheet.getEmployeeName());

				notificationService.createNotification(
						message,
						managerId,
						timesheet.getEmployeeId(),
						"RESUBMISSION",
						timesheet.getTimesheetId(),
						existingProject.getProjectId(),
						null);


                // Email to manager
                try {
                    String emailSubject = "Timesheet Resubmitted - " + timesheet.getTimesheetId();
                    String emailBody = String.format(
                            "Dear Manager,\n\n" +
                                    "A previously rejected timesheet has been resubmitted:\n\n" +
                                    "Timesheet ID: %s\n" +
                                    "Employee: %s\n" +
                                    "Project: %s\n" +
                                    "Week: %s to %s\n\n" +
                                    "Please review it in the system.\n\n" +
                                    "Regards,\n" +
                                    "Timesheet System",
                            timesheet.getTimesheetId(),
                            timesheet.getEmployeeName(),
                            existingProject.getProjectName(),
                            timesheet.getWeekStartDate(),
                            timesheet.getWeekEndDate());

                    mailService.sendEmail(managerEmail, emailSubject, emailBody);
                } catch (Exception e) {
                    System.err.println("Failed to send email notification to manager for timesheet resubmission: " + e.getMessage());
                }

                // Email to employee (confirmation of resubmission)
                try {
                    String emailSubject = "Timesheet Resubmitted - " + timesheet.getTimesheetId();
                    String emailBody = String.format(
                            "Dear %s,\n\n" +
                                    "You have successfully resubmitted your timesheet:\n\n" +
                                    "Timesheet ID: %s\n" +
                                    "Project: %s\n" +
                                    "Week: %s to %s\n\n" +
                                    "Your manager has been notified for review.\n\n" +
                                    "Regards,\n" +
                                    "Timesheet System",
                            timesheet.getEmployeeName(),
                            timesheet.getTimesheetId(),
                            existingProject.getProjectName(),
                            timesheet.getWeekStartDate(),
                            timesheet.getWeekEndDate());

                    mailService.sendEmail(employeeEmail, emailSubject, emailBody);
                } catch (Exception e) {
                    System.err.println("Failed to send confirmation email to employee for timesheet resubmission: " + e.getMessage());
                }

			} else {
				System.out.println("Project ID " + projectDto.getProjectId() + " is not a rejected project, skipping.");
			}
		}

		timesheet.setStatus(TimesheetStatus.SUBMITTED);

		System.out.println("Completed updateRejectedTimesheet method");
	}

	// Fetching all timesheets excluding those with DRAFT status for admin side
	@Override
	public List<TimesheetDto> getAllTimesheetsExcludingDrafts() {
		System.out.println("Fetching all timesheets excluding DRAFT status");

		// Get all timesheets that are not DRAFT
		List<Timesheets> timesheets = timesheetRepo.findByStatusNot(TimesheetStatus.DRAFT);

		// Map to DTOs
		return timesheets.stream()
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}

	private void notifyManagerOnSubmission(Timesheets timesheet) {
		for (ProjectEntry project : timesheet.getProjects()) {
            // Get manager and employee details
            String managerId = projectService.getProjectManagerId(project.getProjectId());
            String managerEmail = projectService.getProjectManagerEmail(project.getProjectId());
            String employeeEmail = employeeService.getEmployeeEmail(timesheet.getEmployeeId());

            // Create notification for manager
			String message = String.format(
					"Timesheet %s for project %s has been submitted by %s",
					timesheet.getTimesheetId(),
					project.getProjectName(),
					timesheet.getEmployeeName());

			notificationService.createNotification(
					message,
					managerId,
					timesheet.getEmployeeId(),
					"SUBMISSION",
					timesheet.getTimesheetId(),
					project.getProjectId(),
					null);

            // Email to manager
            try {
                String emailSubject = "Timesheet Submitted - " + timesheet.getTimesheetId();
                String emailBody = String.format(
                        "Dear Manager,\n\n" +
                                "A new timesheet has been submitted for your approval:\n\n" +
                                "Timesheet ID: %s\n" +
                                "Employee: %s\n" +
                                "Project: %s\n" +
                                "Week: %s to %s\n\n" +
                                "Please review it in the system.\n\n" +
                                "Regards,\n" +
                                "Timesheet System",
                        timesheet.getTimesheetId(),
                        timesheet.getEmployeeName(),
                        project.getProjectName(),
                        timesheet.getWeekStartDate(),
                        timesheet.getWeekEndDate());

                mailService.sendEmail(managerEmail, emailSubject, emailBody);
            } catch (Exception e) {
                System.err.println("Failed to send email notification to manager for timesheet submission: " + e.getMessage());
            }

            // Email to employee (confirmation of submission)
            try {
                String emailSubject = "Timesheet Submission Confirmation - " + timesheet.getTimesheetId();
                String emailBody = String.format(
                        "Dear %s,\n\n" +
                                "Your timesheet has been successfully submitted:\n\n" +
                                "Timesheet ID: %s\n" +
                                "Project: %s\n" +
                                "Week: %s to %s\n\n" +
                                "Your manager has been notified for approval.\n\n" +
                                "Regards,\n" +
                                "Timesheet System",
                        timesheet.getEmployeeName(),
                        timesheet.getTimesheetId(),
                        project.getProjectName(),
                        timesheet.getWeekStartDate(),
                        timesheet.getWeekEndDate());

                mailService.sendEmail(employeeEmail, emailSubject, emailBody);
            } catch (Exception e) {
                System.err.println("Failed to send confirmation email to employee for timesheet submission: " + e.getMessage());
            }
		}
	}

    private void notifyEmployeeOnStatusChange(Timesheets timesheet, Long projectId,
                                              TimesheetStatus newStatus, String rejectionReason) {
        ProjectEntry project = timesheet.getProjects().stream()
                .filter(p -> p.getProjectId().equals(projectId))
                .findFirst()
                .orElse(null);

        if (project != null) {
            // Get manager and employee details
            String managerId = projectService.getProjectManagerId(project.getProjectId());
            String managerEmail = projectService.getProjectManagerEmail(project.getProjectId());
            String employeeEmail = employeeService.getEmployeeEmail(timesheet.getEmployeeId());

            // Common message parts
            String statusAction = newStatus == TimesheetStatus.APPROVED ? "approved" : "rejected";
            String notificationType = newStatus == TimesheetStatus.APPROVED ? "APPROVAL" : "REJECTION";

            // Message for notification
            String message = String.format(
                    "Your project %s in timesheet %s has been %s%s",
                    project.getProjectName(),
                    timesheet.getTimesheetId(),
                    statusAction,
                    newStatus == TimesheetStatus.REJECTED ? ". Reason: " + rejectionReason : "");

            // Create notification for employee
            notificationService.createNotification(
                    message,
                    timesheet.getEmployeeId(),
                    managerId,
                    notificationType,
                    timesheet.getTimesheetId(),
                    project.getProjectId(),
                    rejectionReason);

            // Email to employee
            try {
                String emailSubject = String.format("Timesheet %s - %s",
                        newStatus == TimesheetStatus.APPROVED ? "Approved" : "Rejected",
                        timesheet.getTimesheetId());

                String emailBody = newStatus == TimesheetStatus.APPROVED
                        ? String.format(
                        "Dear %s,\n\n" +
                                "Your timesheet has been approved:\n\n" +
                                "Timesheet ID: %s\n" +
                                "Project: %s\n" +
                                "Week: %s to %s\n\n" +
                                "Regards,\n" +
                                "Timesheet System",
                        timesheet.getEmployeeName(),
                        timesheet.getTimesheetId(),
                        project.getProjectName(),
                        timesheet.getWeekStartDate(),
                        timesheet.getWeekEndDate())
                        : String.format(
                        "Dear %s,\n\n" +
                                "Your timesheet has been rejected:\n\n" +
                                "Timesheet ID: %s\n" +
                                "Project: %s\n" +
                                "Week: %s to %s\n" +
                                "Reason: %s\n\n" +
                                "Please review and resubmit.\n\n" +
                                "Regards,\n" +
                                "Timesheet System",
                        timesheet.getEmployeeName(),
                        timesheet.getTimesheetId(),
                        project.getProjectName(),
                        timesheet.getWeekStartDate(),
                        timesheet.getWeekEndDate(),
                        rejectionReason);

                mailService.sendEmail(employeeEmail, emailSubject, emailBody);
            } catch (Exception e) {
                System.err.println("Failed to send email notification to employee for timesheet status update: " + e.getMessage());
            }

            // Email to manager (confirmation of action)
            try {
                String emailSubject = String.format("Timesheet %s Confirmation - %s",
                        newStatus == TimesheetStatus.APPROVED ? "Approval" : "Rejection",
                        timesheet.getTimesheetId());

                String emailBody = String.format(
                        "Dear Manager,\n\n" +
                                "You have %s the following timesheet:\n\n" +
                                "Timesheet ID: %s\n" +
                                "Employee: %s\n" +
                                "Project: %s\n" +
                                "Week: %s to %s\n" +
                                "%s" + // Reason if rejected
                                "\n\nRegards,\n" +
                                "Timesheet System",
                        statusAction,
                        timesheet.getTimesheetId(),
                        timesheet.getEmployeeName(),
                        project.getProjectName(),
                        timesheet.getWeekStartDate(),
                        timesheet.getWeekEndDate(),
                        newStatus == TimesheetStatus.REJECTED ? "Reason: " + rejectionReason + "\n" : "");

                mailService.sendEmail(managerEmail, emailSubject, emailBody);
            } catch (Exception e) {
                System.err.println("Failed to send confirmation email to manager for timesheet status update: " + e.getMessage());
            }
        }
    }

	// Fetching timesheets for employees reporting to a specific Reporting manager
	@Override
	public List<TimesheetDto> getTimesheetsByReportingManager(String reportingManagerId) {
		System.out.println("Fetching timesheets for employees reporting to manager: " + reportingManagerId);

		// 1. Get all employees reporting to this manager
		List<UserDTO> reportingEmployees = employeeService.getEmployeesByReportingManagerId(reportingManagerId);
		List<String> employeeIds = reportingEmployees.stream()
				.map(UserDTO::getEmpId)
				.collect(Collectors.toList());

		if (employeeIds.isEmpty()) {
			System.out.println("No employees found reporting to manager: " + reportingManagerId);
			return Collections.emptyList();
		}

		System.out.println("Found " + employeeIds.size() + " employees reporting to this manager");

		// 2. Get all timesheets for these employees (excluding DRAFT & Cancelled status)
		List<Timesheets> timesheets = timesheetRepo.findByEmployeeIdInAndStatusNotIn(
				employeeIds, Arrays.asList(TimesheetStatus.DRAFT, TimesheetStatus.CANCELLED)
		);

		// 3. Convert to DTOs
		return timesheets.stream()
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}

	// Fetching a timesheet by its ID and filtering projects by project ID
	@Override
	public TimesheetDto getTimesheetByTimesheetIdAndProjectId(String timesheetId, Long projectId) {
		System.out.println("Fetching timesheet " + timesheetId + " with project " + projectId);

		TimesheetDto fullTimesheet = getTimesheetById(timesheetId);
		if (fullTimesheet == null) {
			throw new RuntimeException("Timesheet not found with ID: " + timesheetId);
		}

		// Filter projects to only include the requested project
		List<ProjectEntryDto> filteredProjects = fullTimesheet.getProjects().stream()
				.filter(project -> project.getProjectId().equals(projectId))
				.collect(Collectors.toList());

		if (filteredProjects.isEmpty()) {
			throw new RuntimeException("Project not found in timesheet: " + projectId);
		}

		// Create a new DTO with only the filtered project
		TimesheetDto responseDto = new TimesheetDto();
		responseDto.setTimesheetId(fullTimesheet.getTimesheetId());
		responseDto.setWeekStartDate(fullTimesheet.getWeekStartDate());
		responseDto.setWeekEndDate(fullTimesheet.getWeekEndDate());
		responseDto.setStatus(fullTimesheet.getStatus());
		responseDto.setEmployeeId(fullTimesheet.getEmployeeId());
		responseDto.setEmployeeName(fullTimesheet.getEmployeeName());
		responseDto.setSubmitted_at(fullTimesheet.getSubmitted_at());
		responseDto.setCreatedAt(fullTimesheet.getCreatedAt());
		responseDto.setUpdatedAt(fullTimesheet.getUpdatedAt());
		responseDto.setProjects(filteredProjects);

		return responseDto;
	}

	@Override
	public List<UserDTO> getUsersWithPendingTimesheetsForCurrentWeek() {
		System.out.println("##### [Service] Fetching employees with DRAFT or MISSING timesheets for Previous week...");

		LocalDate today = LocalDate.now();
//		LocalDate weekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate weekEnd = weekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

		// 1. Get all active projects
		List<Project> activeProjects = projectService.getProjectsByStatus("STARTED");
		System.out.println("##### [Service] Active projects found: " + activeProjects.size());

		Set<String> affectedEmpIds = new HashSet<>();

		for (Project project : activeProjects) {
			Long projectId = project.getId();

			for (Assignment assignment : project.getAssignments()) {
				String empId = assignment.getEmpId();

				List<Timesheets> empTimesheets = timesheetRepo.findByEmployeeIdAndWeekStartDateBetween(empId, prevWeekStart, prevWeekEnd);

				boolean hasSubmitted = empTimesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.SUBMITTED &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				boolean hasDraft = empTimesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.DRAFT &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				// If not submitted and either draft or missing -> add to result
				if (!hasSubmitted && (hasDraft || empTimesheets.isEmpty())) {
					affectedEmpIds.add(empId);
				}
			}
		}

		System.out.println("##### [Service] Total affected employee IDs: " + affectedEmpIds.size());

		if (affectedEmpIds.isEmpty()) {
			return Collections.emptyList();
		}

		// 2. Fetch unique users
		List<OurUsers> users = usersRepo.findByEmpIdIn(new ArrayList<>(affectedEmpIds));

		// 3. Map to unique UserDTO by name/email
		Map<String, UserDTO> uniqueMap = new HashMap<>();

		for (OurUsers user : users) {
			String key = user.getName() + "|" + user.getEmail(); // unique key
			if (!uniqueMap.containsKey(key)) {
				UserDTO dto = new UserDTO();
				dto.setName(user.getName());
				dto.setEmail(user.getEmail());
				uniqueMap.put(key, dto);
			}
		}

		System.out.println("##### [Service] Returning " + uniqueMap.size() + " unique UserDTOs");
		return new ArrayList<>(uniqueMap.values());
	}


}
