package com.phegondev.usersmanagementsystem.service;
 
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
 
import org.springframework.stereotype.Service;
 
import com.phegondev.usersmanagementsystem.dto.ProjectNameIdDTO;
import com.phegondev.usersmanagementsystem.dto.SendEmailRequestDTO;
import com.phegondev.usersmanagementsystem.dto.TaskNameIdDTO;
import com.phegondev.usersmanagementsystem.dto.TimesheetDTO;
import com.phegondev.usersmanagementsystem.dto.TimesheetInitialDataDTO;
import com.phegondev.usersmanagementsystem.dto.UserSummaryDTO;
import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.repository.AssignmentRepository;
import com.phegondev.usersmanagementsystem.repository.ProjectRepository;
import com.phegondev.usersmanagementsystem.repository.TaskRepository;
import com.phegondev.usersmanagementsystem.repository.TimesheetRepo;
 import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.service.NotificationService;

import jakarta.mail.MessagingException;


@Service
public class TimesheetService {
 
	private final TimesheetRepo timesheetRepo;
	private final ProjectRepository projectRepository;
	private final AssignmentRepository assignmentRepository;
	private final TaskRepository taskRepository;
    private final NotificationService notificationService;
	private final MailService mailService;

	private final UsersRepo usersRepo;
 
	public TimesheetService(TimesheetRepo timesheetRepo, ProjectRepository projectRepository,
			AssignmentRepository assignmentRepository, TaskRepository taskRepository, UsersRepo usersRepo,NotificationService notificationService, MailService mailService) {
		this.timesheetRepo = timesheetRepo;
		this.projectRepository = projectRepository;
		this.assignmentRepository = assignmentRepository;
		this.taskRepository = taskRepository;
		this.usersRepo = usersRepo;
		this.notificationService = notificationService;
		this.mailService = mailService;
	}
 
	public TimesheetDTO createTimesheet(TimesheetDTO dto) {
 
		if (timesheetRepo.existsByTimesheetIdAndTaskId(dto.getTimesheetId(), dto.getTaskId())) {
			throw new RuntimeException("This task already exists for the given timesheet.");
		}
 
		Timesheet timesheet = new Timesheet();
		timesheet.setTimesheetId(dto.getTimesheetId());
		timesheet.setEmpId(dto.getEmpId());
		timesheet.setEmpName(dto.getEmpName());
		timesheet.setProjectId(dto.getProjectId());
		timesheet.setTaskId(dto.getTaskId());
		timesheet.setProjectName(dto.getProjectName());
		timesheet.setTaskName(dto.getTaskName());
		timesheet.setTimeCategory(dto.getTimeCategory());
		timesheet.setResourcePlan(dto.getResourcePlan());
		timesheet.setMondayHours(dto.getMondayHours());
		timesheet.setTuesdayHours(dto.getTuesdayHours());
		timesheet.setWednesdayHours(dto.getWednesdayHours());
		timesheet.setThursadyHours(dto.getThursadyHours());
		timesheet.setFridayHours(dto.getFridayHours());
		timesheet.setSaturdayHours(dto.getSaturdayHours());
		timesheet.setSundayHours(dto.getSundayHours());
		timesheet.setComments(dto.getComments());
		timesheet.setStatus("DRAFT");
		
		LocalDate today = LocalDate.now();
	    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
	    LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
 
		timesheet.setWeekStart(weekStart);
		timesheet.setWeekEnd(weekEnd);
		
		
 
		System.out.println("Saving Timesheet Entity: " + timesheet);
		Timesheet saved = timesheetRepo.save(timesheet);
 
		TimesheetDTO responseDto = mapToDTO(saved);
		System.out.println("Mapped Saved Entity to DTO: " + responseDto);
		return responseDto;
	}
 
	private Timesheet mapToEntity(TimesheetDTO dto) {
		Timesheet timesheet = new Timesheet();
		timesheet.setTimesheetId(dto.getTimesheetId());
		timesheet.setEmpId(dto.getEmpId());
		timesheet.setEmpName(dto.getEmpName());
		timesheet.setProjectId(dto.getProjectId());
		timesheet.setTaskId(dto.getTaskId());
		timesheet.setProjectName(dto.getProjectName());
		timesheet.setTaskName(dto.getTaskName());
		timesheet.setTimeCategory(dto.getTimeCategory());
		timesheet.setResourcePlan(dto.getResourcePlan());
		timesheet.setMondayHours(dto.getMondayHours());
		timesheet.setTuesdayHours(dto.getTuesdayHours());
		timesheet.setWednesdayHours(dto.getWednesdayHours());
		timesheet.setThursadyHours(dto.getThursadyHours());
		timesheet.setFridayHours(dto.getFridayHours());
		timesheet.setSaturdayHours(dto.getSaturdayHours());
		timesheet.setSundayHours(dto.getSundayHours());
		timesheet.setComments(dto.getComments());
		timesheet.setStatus("DRAFT");
		return timesheet;
	}
 
	public TimesheetDTO mapToDTO(Timesheet timesheet) {
	    TimesheetDTO dto = new TimesheetDTO();
	    dto.setId(timesheet.getId());
	    dto.setTimesheetId(timesheet.getTimesheetId());
	    dto.setEmpId(timesheet.getEmpId());
	    dto.setEmpName(timesheet.getEmpName());
	    dto.setProjectId(timesheet.getProjectId());
	    dto.setTaskId(timesheet.getTaskId());
	    dto.setProjectName(timesheet.getProjectName());
	    dto.setTaskName(timesheet.getTaskName());
	    dto.setTimeCategory(timesheet.getTimeCategory());
	    dto.setResourcePlan(timesheet.getResourcePlan());
	    
	    dto.setMondayHours(timesheet.getMondayHours());
	    dto.setTuesdayHours(timesheet.getTuesdayHours());
	    dto.setWednesdayHours(timesheet.getWednesdayHours());
	    dto.setThursadyHours(timesheet.getThursadyHours());
	    dto.setFridayHours(timesheet.getFridayHours());
	    dto.setSaturdayHours(timesheet.getSaturdayHours());
	    dto.setSundayHours(timesheet.getSundayHours());
 
	    dto.setComments(timesheet.getComments());
 
	    dto.setCreatedAt(timesheet.getCreatedAt());
	    dto.setUpdatedAt(timesheet.getUpdatedAt());
	    dto.setStatus(timesheet.getStatus());
	    dto.setSubmitted_at(timesheet.getSubmitted_at());
 
	    dto.setWeekStart(timesheet.getWeekStart());
	    dto.setWeekEnd(timesheet.getWeekEnd());
 
	    return dto;
	}
 
 
	private String generateTimesheetId() {
		Timesheet latest = timesheetRepo.findLatestTimesheet();
		int nextNumber;
 
		System.out.println("🔍 Latest timesheet fetched: " + (latest != null ? latest.getTimesheetId() : "null"));
 
		if (latest == null || latest.getTimesheetId() == null) {
			nextNumber = 101;
			System.out.println("📌 No existing timesheet found. Starting from: " + nextNumber);
		} else {
			String[] parts = latest.getTimesheetId().split("-");
			int lastNumber = Integer.parseInt(parts[1]);
			nextNumber = lastNumber + 1;
			System.out.println("🔢 Last number extracted: " + lastNumber + ", Next number: " + nextNumber);
		}
 
		String newId = String.format("TS-%d", nextNumber);
		System.out.println("🆔 Generated Timesheet ID: " + newId);
		return newId;
	}
 
	public TimesheetInitialDataDTO getInitialData(String empId) {
 
		System.out.println("📥 [Service] getInitialData() called with empId: " + empId);
 
		String generatedTimesheetId = generateTimesheetId();
		System.out.println("🆔 [Service] Generated Timesheet ID: " + generatedTimesheetId);
 
		List<ProjectNameIdDTO> projectNameIds = null;
		//assignmentRepository.findProjectIdAndNameByEmpId(empId);
		System.out.println("📦 [Service] Projects retrieved for empId " + empId + ":");
 
		for (ProjectNameIdDTO dto : projectNameIds) {
			System.out.println("➡️ Project ID: " + dto.getProjectId() + ", Project Name: " + dto.getProjectName());
		}
 
		TimesheetInitialDataDTO responseDto = new TimesheetInitialDataDTO(generatedTimesheetId, empId, projectNameIds);
		System.out.println("✅ [Service] Returning TimesheetInitialDataDTO: " + responseDto);
 
		return responseDto;
	}
 
	public List<TaskNameIdDTO> getTasksByProjectId(Long projectId) {
		System.out.println("📥 Fetching task list for projectId: " + projectId);
		List<TaskNameIdDTO> tasks = null;
		taskRepository.findTaskIdAndTitleByProjectId(projectId);
		tasks.forEach(task -> System.out.println("➡️ Task: " + task));
		return tasks;
	}
 
	public TimesheetDTO updateTimesheet(String timesheetId, TimesheetDTO dto) {
		System.out.println("Fetching Timesheet by ID: " + timesheetId);
		Timesheet existing = timesheetRepo.findByTimesheetId(timesheetId)
				.orElseThrow(() -> new RuntimeException("Timesheet not found for ID: " + timesheetId));
 
		System.out.println("Existing Timesheet before update: " + existing);
 
		// Update only hours and comments
		existing.setMondayHours(dto.getMondayHours());
		existing.setTuesdayHours(dto.getTuesdayHours());
		existing.setWednesdayHours(dto.getWednesdayHours());
		existing.setThursadyHours(dto.getThursadyHours());
		existing.setFridayHours(dto.getFridayHours());
		existing.setSaturdayHours(dto.getSaturdayHours());
		existing.setSundayHours(dto.getSundayHours());
		existing.setComments(dto.getComments());
 
		System.out.println("Modified Timesheet before saving: " + existing);
 
		Timesheet updated = timesheetRepo.save(existing);
		System.out.println("Saved Timesheet: " + updated);
 
		TimesheetDTO responseDto = mapToDTO(updated);
		System.out.println("Mapped Timesheet to DTO: " + responseDto);
 
		return responseDto;
	}
 
	public TimesheetDTO getTimesheetById(String timesheetId) {
		Timesheet timesheet = timesheetRepo.findByTimesheetId(timesheetId)
				.orElseThrow(() -> new RuntimeException("Timesheet not found for ID: " + timesheetId));
 
		System.out.println("Found Timesheet Entity: " + timesheet);
		return mapToDTO(timesheet);
	}
 
	public List<TimesheetDTO> getTimesheetsByEmpId(String empId) {
		List<Timesheet> timesheetList = timesheetRepo.findByEmpId(empId);
 
		System.out.println("Found Timesheet Entities: " + timesheetList);
 
		return timesheetList.stream()
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}
 
	public List<TimesheetDTO> getCurrentWeekTimesheets(String empId) {
		LocalDate today = LocalDate.now();
 
		// Adjust to get Monday as the first day of the week
		LocalDate weekStart = today.with(DayOfWeek.MONDAY);
		LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
 
		LocalDateTime startDateTime = weekStart.atStartOfDay();
		LocalDateTime endDateTime = weekEnd.atTime(LocalTime.MAX);
 
		System.out.println("Current week range: " + startDateTime + " to " + endDateTime);
 
		List<Timesheet> timesheets = timesheetRepo.findByEmpIdAndCreatedAtBetween(empId, startDateTime, endDateTime);
 
		return timesheets.stream()
				.map(this::mapToDTO)
				.collect(Collectors.toList());
	}
 
	// Submit all drafts for a given employee
	public List<Timesheet> findByEmployeeIdAndStatus(String empId, String status) {
		return timesheetRepo.findByEmpIdAndStatus(empId, status);
	}
 
	public List<Timesheet> updateTimesheetsStatus(List<Timesheet> timesheets, String newStatus) {
		// For submitted timesheets, set the submission timestamp
		if ("SUBMITTED".equals(newStatus)) {
			LocalDateTime now = LocalDateTime.now();
			timesheets.forEach(t -> t.setSubmitted_at(now));
		}
		timesheets.forEach(t -> t.setStatus(newStatus));
		return timesheetRepo.saveAll(timesheets);
	}
 
	public void deleteTimesheetById(Long id) {
		System.out.println("Attempting to delete timesheet with ID: " + id);
		Timesheet timesheet = timesheetRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("Timesheet not found with ID: " + id));
		timesheetRepo.delete(timesheet);
		System.out.println("Successfully deleted timesheet with ID: " + id);
	}
 
	// // submit single timesheet
	// public TimesheetDTO submitTimesheet(Long id) {
	// 	System.out.println("Submitting timesheet with ID: " + id);
	// 	Timesheet existing = timesheetRepo.findById(id)
	// 			.orElseThrow(() -> new RuntimeException("Timesheet not found with ID: " + id));
 
	// 	System.out.println("Current status: " + existing.getStatus());
 
	// 	// Only allow submission if in DRAFT status
	// 	if (!"DRAFT".equals(existing.getStatus()) && !"REJECTED".equals(existing.getStatus())) {
	// 		throw new RuntimeException("Only DRAFT or REJECTED timesheets can be submitted");
	// 	}
 
	// 	existing.setStatus("SUBMITTED");
	// 	existing.setSubmitted_at(LocalDateTime.now());
	// 	Timesheet updated = timesheetRepo.save(existing);
 
	// 	System.out.println("Updated status: " + updated.getStatus());
	// 	return mapToDTO(updated);
	// }
 



	// Modify submitTimesheet method
public TimesheetDTO submitTimesheet(Long id) {
    System.out.println("Submitting timesheet with ID: " + id);
    Timesheet existing = timesheetRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Timesheet not found with ID: " + id));

    System.out.println("Current status: " + existing.getStatus());

    if (!"DRAFT".equals(existing.getStatus()) && !"REJECTED".equals(existing.getStatus())) {
        throw new RuntimeException("Only DRAFT or REJECTED timesheets can be submitted");
    }

    existing.setStatus("SUBMITTED");
    existing.setSubmitted_at(LocalDateTime.now());
    Timesheet updated = timesheetRepo.save(existing);

    // Send notification to admin
    String adminId = "admin"; // Or fetch admin ID from database
    String message = existing.getEmpName() + " has submitted timesheet " + existing.getTimesheetId();
    notificationService.createNotification(
        message,
        adminId,
        existing.getEmpId(),
        "SUBMISSION",
        existing.getTimesheetId()
    );

    System.out.println("Updated status: " + updated.getStatus());
    return mapToDTO(updated);
}
 
	public List<TimesheetDTO> getAllNonDraftTimesheets() {
	    System.out.println("[Service] Calling repository to fetch timesheets where status != 'DRAFT'");
 
	    List<Timesheet> timesheets = timesheetRepo.findByStatusNot("DRAFT");
 
	    System.out.println("[Service] Fetched " + timesheets.size() + " non-draft timesheets from DB");
 
	    List<TimesheetDTO> dtoList = timesheets.stream()
	                                           .map(this::mapToDTO)
	                                           .collect(Collectors.toList());
 
	    System.out.println("[Service] Mapped to DTO list with size: " + dtoList.size());
	    return dtoList;
	}
	
	// public void updateStatus(String timesheetId, String status) {
	//     System.out.println("[Service] Updating status for Timesheet ID: " + timesheetId);
 
	//     Timesheet timesheet = timesheetRepo.findByTimesheetId(timesheetId)
	//         .orElseThrow(() -> new RuntimeException("Timesheet not found for ID: " + timesheetId));
 
	//     System.out.println("[Service] Current status: " + timesheet.getStatus());
	//     System.out.println("[Service] New status to set: " + status);
 
	//     // Optionally validate allowed statuses
	//     if (!status.equalsIgnoreCase("APPROVED") && !status.equalsIgnoreCase("REJECTED")) {
	//         throw new IllegalArgumentException("Invalid status. Allowed: APPROVED or REJECTED");
	//     }
 
	//     timesheet.setStatus(status.toUpperCase());
	//     timesheet.setUpdatedAt(LocalDateTime.now());
 
	//     timesheetRepo.save(timesheet);
 
	//     System.out.println("[Service] Status update complete for Timesheet ID: " + timesheetId);
	// }

	// public void updateStatus(String timesheetId, String status) {
	//     System.out.println("[Service] Updating status for Timesheet ID: " + timesheetId);
 
	//     Timesheet timesheet = timesheetRepo.findByTimesheetId(timesheetId)
	//         .orElseThrow(() -> new RuntimeException("Timesheet not found for ID: " + timesheetId));
 
	//     System.out.println("[Service] Current status: " + timesheet.getStatus());
	//     System.out.println("[Service] New status to set: " + status);
 
	//     // Optionally validate allowed statuses
	//     if (!status.equalsIgnoreCase("APPROVED") && !status.equalsIgnoreCase("REJECTED")) {
	//         throw new IllegalArgumentException("Invalid status. Allowed: APPROVED or REJECTED");
	//     }
 
	//     timesheet.setStatus(status.toUpperCase());
	//     timesheet.setUpdatedAt(LocalDateTime.now());
 
	//     timesheetRepo.save(timesheet);
 
	//     System.out.println("[Service] Status update complete for Timesheet ID: " + timesheetId);
	// }



	// Modify updateStatus method
public void updateStatus(String timesheetId, String status) {
    System.out.println("[Service] Updating status for Timesheet ID: " + timesheetId);

    Timesheet timesheet = timesheetRepo.findByTimesheetId(timesheetId)
        .orElseThrow(() -> new RuntimeException("Timesheet not found for ID: " + timesheetId));

    System.out.println("[Service] Current status: " + timesheet.getStatus());
    System.out.println("[Service] New status to set: " + status);

    if (!status.equalsIgnoreCase("APPROVED") && !status.equalsIgnoreCase("REJECTED")) {
        throw new IllegalArgumentException("Invalid status. Allowed: APPROVED or REJECTED");
    }

    timesheet.setStatus(status.toUpperCase());
    timesheet.setUpdatedAt(LocalDateTime.now());
    timesheetRepo.save(timesheet);

    // Send notification to user
    String message;
    if (status.equalsIgnoreCase("APPROVED")) {
        message = "Your timesheet " + timesheetId + " has been approved. Thank you!";
    } else {
        message = "Your timesheet " + timesheetId + " has been rejected. Please resubmit.";
    }
    
    notificationService.createNotification(
        message,
        timesheet.getEmpId(),
        "admin", // or actual admin ID
        status.equalsIgnoreCase("APPROVED") ? "APPROVAL" : "REJECTION",
        timesheetId
    );

    System.out.println("[Service] Status update complete for Timesheet ID: " + timesheetId);
}

public List<UserSummaryDTO> getAllUserSummaries() {
	    System.out.println("Fetching all users from database...");
 
	    List<UserSummaryDTO> summaryList = usersRepo.findAll().stream()
	            .map(user -> {
	                System.out.println("Mapping user : " + user.getEmpId() + " - " + user.getEmail());
	                return new UserSummaryDTO(user.getEmail(), user.getEmpId(), user.getName());
	            })
	            .collect(Collectors.toList());
 
	    System.out.println("Total users created: " + summaryList.size());
	    return summaryList;
	}
	
	
	 public void sendPersonalizedEmails(SendEmailRequestDTO request) {
	        for (UserSummaryDTO recipient : request.getTo()) {
	            String personalizedBody = "Dear " + recipient.getName() + ",\n\n" + request.getMessageBody();
 
	            try {
	            	mailService.sendEmail(
	                    recipient.getEmail(),
	                    request.getCc(),
	                    "Timesheet Reminder",
	                    personalizedBody
	                );
	                System.out.println("✅ Email sent to: " + recipient.getEmail());
 
	            } catch (Exception e) {
	                System.err.println("❌ Failed to send email to: " + recipient.getEmail());
	                e.printStackTrace();
	            }
	        }
 
	        System.out.println("📨 All email requests processed.");
	    }
 
 
 
	// // Get all submitted timesheets on admin side
	// public List<TimesheetDTO> getAllSubmittedTimesheets() {
	// List<Timesheet> timesheets = timesheetRepo.findAllSubmittedTimesheets();
	// return timesheets.stream().map(this::mapToDTO).collect(Collectors.toList());
	// }
 
}