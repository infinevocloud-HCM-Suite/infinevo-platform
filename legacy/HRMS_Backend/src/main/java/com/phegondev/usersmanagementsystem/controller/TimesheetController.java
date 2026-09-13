package com.phegondev.usersmanagementsystem.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.phegondev.usersmanagementsystem.service.MailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.phegondev.usersmanagementsystem.dto.SendEmailRequestDTO;
import com.phegondev.usersmanagementsystem.dto.TaskNameIdDTO;
import com.phegondev.usersmanagementsystem.dto.TimesheetDTO;
import com.phegondev.usersmanagementsystem.dto.TimesheetInitialDataDTO;
import com.phegondev.usersmanagementsystem.dto.UserSummaryDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.service.TimesheetService;

@RestController
public class TimesheetController {

	private final TimesheetService timesheetService;
	private final MailService mailService;

	public TimesheetController(TimesheetService timesheetService, MailService mailService) {
		this.timesheetService = timesheetService;
		this.mailService = mailService;
	}

	@PostMapping("/timesheet")
	public ResponseEntity<TimesheetDTO> createTimesheet(@RequestBody TimesheetDTO dto) {
		System.out.println("Received DTO in Controller: " + dto);
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();
		String empName = user.getName();
		dto.setEmpName(empName);
		System.out.println("🔐 Authenticated user empName: " + empName);
		TimesheetDTO created = timesheetService.createTimesheet(dto);
		System.out.println("Created DTO from Service: " + created);
		return ResponseEntity.ok(created);
	}

	@GetMapping("/timesheet/get-initial-data")
	public ResponseEntity<TimesheetInitialDataDTO> getInitialData() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();

		String empId = user.getEmpId();
		System.out.println("🔐 Authenticated user empId: " + empId);

		TimesheetInitialDataDTO response = timesheetService.getInitialData(empId);
		System.out.println("✅ TimesheetInitialDataDTO returned: " + response);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/timesheet/get-tasks/{projectId}")
	public ResponseEntity<List<TaskNameIdDTO>> getTasksByProject(@PathVariable Long projectId) {
		System.out.println("🎯 API call received for projectId: " + projectId);
		List<TaskNameIdDTO> taskList = timesheetService.getTasksByProjectId(projectId);
		return ResponseEntity.ok(taskList);
	}

	@PutMapping("/timesheet/{timesheetId}")
	public ResponseEntity<TimesheetDTO> updateTimesheet(
			@PathVariable String timesheetId,
			@RequestBody TimesheetDTO dto) {

		System.out.println("Received update request for Timesheet ID: " + timesheetId);
		System.out.println("Incoming DTO: " + dto);

		TimesheetDTO updated = timesheetService.updateTimesheet(timesheetId, dto);

		System.out.println("Updated Timesheet DTO: " + updated);
		return ResponseEntity.ok(updated);
	}

	@GetMapping("/timesheet/{timesheetId}")
	public ResponseEntity<TimesheetDTO> getTimesheetById(@PathVariable String timesheetId) {
		System.out.println("Fetching Timesheet for ID: " + timesheetId);

		TimesheetDTO dto = timesheetService.getTimesheetById(timesheetId);

		System.out.println("Fetched Timesheet DTO: " + dto);
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/timesheets/{timesheetId}")
	public ResponseEntity<TimesheetDTO> getTimesheetDetailsForAdmin(@PathVariable String timesheetId) {
		System.out.println("[Admin] Fetching details for Timesheet ID: " + timesheetId);
		try {
			TimesheetDTO dto = timesheetService.getTimesheetById(timesheetId);

			if (dto == null) {
				return ResponseEntity.notFound().build();
			}

			System.out.println("[Admin] Fetched Timesheet DTO: " + dto);
			return ResponseEntity.ok(dto);
		} catch (Exception e) {
			System.out.println("[Admin] Error fetching timesheet details: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/timesheet/employee")
	public ResponseEntity<List<TimesheetDTO>> getTimesheetsByEmpId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();
		String empId = user.getEmpId();
		System.out.println("🔐 Authenticated user empId: " + empId);

		List<TimesheetDTO> timesheets = timesheetService.getTimesheetsByEmpId(empId);

		System.out.println("Fetched Timesheet List: " + timesheets);
		return ResponseEntity.ok(timesheets);
	}

	@GetMapping("/timesheets/non-draft")
	public ResponseEntity<List<TimesheetDTO>> getNonDraftTimesheets() {
		System.out.println("[Controller] Fetching all non-draft timesheets...");

		List<TimesheetDTO> list = timesheetService.getAllNonDraftTimesheets();

		System.out.println("[Controller] Total non-draft timesheets returned: " + list.size());
		return ResponseEntity.ok(list);
	}

	@GetMapping("/timesheet/employee/current-week")
	public ResponseEntity<List<TimesheetDTO>> getCurrentWeekTimesheetsByEmpId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();
		String empId = user.getEmpId();
		System.out.println("🔐 Authenticated user empId: " + empId);
		List<TimesheetDTO> result = timesheetService.getCurrentWeekTimesheets(empId);
		return ResponseEntity.ok(result);
	}

	// put to submit all the drafts
	@PutMapping("/timesheet/submit-all")
	public ResponseEntity<?> submitAllDraftTimesheets() {
		try {
			// Validate token and get employee ID
			// String empId = jwtUtil.extractEmployeeId(token.replace("Bearer ", ""));

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			OurUsers user = (OurUsers) authentication.getPrincipal();
			String empId = user.getEmpId();

			// Find all draft timesheets for this employee
			List<Timesheet> draftTimesheets = timesheetService.findByEmployeeIdAndStatus(empId, "DRAFT");

			if (draftTimesheets.isEmpty()) {
				return ResponseEntity.ok().body("No draft timesheets found");
			}

			// Set submitted_at timestamp for all drafts
			LocalDateTime now = LocalDateTime.now();
			draftTimesheets.forEach(t -> {
				t.setStatus("SUBMITTED");
				t.setSubmitted_at(now);
			});

			// Update status to SUBMITTED for all drafts
			List<Timesheet> updatedTimesheets = timesheetService.updateTimesheetsStatus(draftTimesheets, "SUBMITTED");

			return ResponseEntity.ok().body(
					"Successfully submitted " + updatedTimesheets.size() + " timesheets");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error submitting timesheets: " + e.getMessage());
		}
	}

	// Delete a timesheet by ID
	@DeleteMapping("/timesheet/{id}")
	public ResponseEntity<?> deleteTimesheet(@PathVariable Long id) {
		try {
			System.out.println("Received delete request for Timesheet ID: " + id);
			timesheetService.deleteTimesheetById(id);
			return ResponseEntity.ok().body("Timesheet entry deleted successfully");
		} catch (RuntimeException e) {
			System.out.println("Error deleting timesheet: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body("Timesheet not found with ID: " + id);
		} catch (Exception e) {
			System.out.println("Error deleting timesheet: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error deleting timesheet: " + e.getMessage());
		}
	}

	// submits specific timesheet
	@PutMapping("/timesheet/{id}/submit")
	public ResponseEntity<?> submitTimesheet(@PathVariable Long id) {
		try {
			TimesheetDTO updated = timesheetService.submitTimesheet(id);
			return ResponseEntity.ok(updated);
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body("Timesheet not found with ID: " + id);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error submitting timesheet: " + e.getMessage());
		}
	}

	// @PutMapping("/timesheets/{timesheetId}/status")
	// public ResponseEntity<String> updateTimesheetStatus(
	// 		@PathVariable String timesheetId,
	// 		@RequestParam String status) {

	// 	System.out.println("[Controller] Received request to update status of Timesheet ID: " + timesheetId);
	// 	System.out.println("[Controller] Requested new status: " + status);

	// 	timesheetService.updateStatus(timesheetId, status);

	// 	System.out.println("[Controller] Status updated successfully");
	// 	return ResponseEntity.ok("Timesheet status updated to: " + status);
	// }

	@PutMapping("/timesheets/{timesheetId}/status")
	public ResponseEntity<String> updateTimesheetStatus(
	        @PathVariable String timesheetId,
	        @RequestParam String status) throws IllegalArgumentException {

	    System.out.println("[Controller] Received request to update status of Timesheet ID: " + timesheetId);
	    System.out.println("[Controller] Requested new status: " + status);

	    try {
	        timesheetService.updateStatus(timesheetId, status);
	        System.out.println("[Controller] Status updated successfully");
	        return ResponseEntity.ok("Timesheet status updated to: " + status);
	    } catch (RuntimeException e) {
	        System.out.println("[Controller] Error: " + e.getMessage());
	        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	    } catch (Exception e) {
	        System.out.println("[Controller] Internal error: " + e.getMessage());
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("Error updating status: " + e.getMessage());
	    }
	}

	@GetMapping("/api/employees/basic-info")
	public ResponseEntity<List<UserSummaryDTO>> getAllUserSummaries() {
	    System.out.println("Received request to fetch all employee summaries...");
	    List<UserSummaryDTO> summaries = timesheetService.getAllUserSummaries();
	    System.out.println("Returning " + summaries.size() + " user summaries to UI.");
	    return ResponseEntity.ok(summaries);
	}
	
    @PostMapping("/api/emails/employee-reminders")
    public ResponseEntity<String> sendEmails(@RequestBody SendEmailRequestDTO request) {
        System.out.println("Received email sending request for " + request.getTo().size() + " recipients");
        timesheetService.sendPersonalizedEmails(request);
        return ResponseEntity.ok("Emails sent successfully.");
    }
 

}