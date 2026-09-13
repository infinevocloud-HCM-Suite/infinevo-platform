package com.phegondev.usersmanagementsystem.controller.timesheet;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.dto.timesheet.ProjectEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetInitialDataDto;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.entity.timesheet.ProjectEntry;
import com.phegondev.usersmanagementsystem.entity.timesheet.Timesheets;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;
import com.phegondev.usersmanagementsystem.service.ProjectService;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetService;

@RestController
@RequestMapping("/api/timesheets")
public class TimesheetsController {

    private final TimesheetService timesheetService;
    private final ProjectService projectService;

    public TimesheetsController(TimesheetService timesheetService, ProjectService projectService) {
        this.timesheetService = timesheetService;
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<String> createTimesheet(@RequestBody TimesheetDto dto) {
        System.out.println("📥 Received request to create timesheet");
        System.out.println("📝 Timesheet ID: " + dto.getTimesheetId());
        System.out.println("👤 Employee ID: " + dto.getEmployeeId() + ", Name: " + dto.getEmployeeName());
        System.out.println("📆 Week: " + dto.getWeekStartDate() + " to " + dto.getWeekEndDate());
        System.out.println("📁 Status: " + dto.getStatus());

        if (dto.getProjects() != null) {
            System.out.println("📦 Project Count: " + dto.getProjects().size());
            dto.getProjects().forEach(project -> {
                System.out
                        .println("  🔧 Project ID: " + project.getProjectId() + ", Name: " + project.getProjectName());
                if (project.getTasks() != null) {
                    System.out.println("    🛠️ Task Count: " + project.getTasks().size());
                    project.getTasks().forEach(task -> {
                        System.out.println("      ✅ Task ID: " + task.getTaskId() + ", Name: " + task.getTaskName());
                        if (task.getDays() != null) {
                            task.getDays().forEach(day -> {
                                System.out.println("        📅 " + day.getDayName() + " (" + day.getDate() + ") - "
                                        + day.getHours() + "h: " + day.getDescription());
                            });
                        }
                    });
                }
            });
        }

        timesheetService.saveTimesheet(dto);
        System.out.println("✅ Timesheet saved successfully.");
        return ResponseEntity.ok("Timesheet saved successfully.");
    }

    @GetMapping("/{timesheetId}")
    public ResponseEntity<TimesheetDto> getTimesheetById(@PathVariable String timesheetId) {
        System.out.println("=== Entering getTimesheetById API ===");
        System.out.println("Received request for timesheetId: " + timesheetId);

        TimesheetDto dto = timesheetService.getTimesheetById(timesheetId);

        if (dto != null) {
            System.out.println("Timesheet found. Returning DTO response.");
            return ResponseEntity.ok(dto);
        } else {
            System.out.println("Timesheet not found for ID: " + timesheetId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/get-initial-data")
    public ResponseEntity<TimesheetInitialDataDto> getInitialData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();

        String empId = user.getEmpId();
        String employeeName = user.getName();
        System.out.println("🔐 Authenticated user empId: " + empId);
        System.out.println("🔐 Authenticated user name: " + employeeName);

        TimesheetInitialDataDto response = timesheetService.getInitialData(empId);
        response.setEmployeeName(employeeName);
        System.out.println("✅ TimesheetInitialDataDTO returned: " + response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/get-tasks/{projectId}")
    public ResponseEntity<List<TaskEntryDto>> getTasksByProject(@PathVariable Long projectId) {
        System.out.println("🎯 API call received for projectId: " + projectId);
        List<TaskEntryDto> taskList = timesheetService.getTasksByProjectId(projectId);
        return ResponseEntity.ok(taskList);
    }

    @GetMapping("/employee/current-week")
    public ResponseEntity<List<TimesheetDto>> getCurrentWeekTimesheetsByEmpId(
            @RequestParam(required = false) String weekStartDate) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();

        System.out.println("🔐 Authenticated user empId: " + empId);

        LocalDate startDate;
        if (weekStartDate != null && !weekStartDate.isEmpty()) {
            startDate = LocalDate.parse(weekStartDate);
        } else {
            // Default to current week if no date provided
            startDate = LocalDate.now().with(DayOfWeek.MONDAY);
        }

        LocalDate endDate = startDate.with(DayOfWeek.SUNDAY);

        System.out.println("📅 Fetching timesheets for week: " + startDate + " to " + endDate);

        List<TimesheetDto> result = timesheetService.getTimesheetsByEmployeeAndWeek(empId, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/employee/filter")
    public ResponseEntity<List<TimesheetDto>> getFilteredTimesheetsByEmpId(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) TimesheetStatus status) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();

        System.out.println("🔐 Authenticated user empId: " + empId);

        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;

        System.out.println("📅 Fetching filtered timesheets with params:");
        System.out.println("Start Date: " + start);
        System.out.println("End Date: " + end);
        System.out.println("Project Name: " + projectName);
        System.out.println("Status: " + status);

        List<TimesheetDto> result = timesheetService.getFilteredTimesheets(empId, start, end, projectName, status);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{timesheetId}")
    public ResponseEntity<?> deleteTimesheet(@PathVariable String timesheetId) {
        try {
            System.out.println("Received delete request for Timesheet ID: " + timesheetId);
            timesheetService.deleteTimesheetByTimesheetId(timesheetId);
            return ResponseEntity.ok().body("Timesheet entry deleted successfully");
        } catch (RuntimeException e) {
            System.out.println("Error deleting timesheet: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Timesheet not found with ID: " + timesheetId);
        } catch (Exception e) {
            System.out.println("Error deleting timesheet: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting timesheet: " + e.getMessage());
        }
    }

    @PutMapping("/{timesheetId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTimesheet(@PathVariable String timesheetId) {
        boolean cancelled = timesheetService.cancelTimesheet(timesheetId);
        Map<String, Object> response = new HashMap<>();
        if (cancelled) {
            response.put("status", "success");
            response.put("message", "Timesheet have been cancelled successfully.");
        } else {
            response.put("status", "failure");
            response.put("message", "Timesheet not found or could not be cancelled.");
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{timesheetId}/submit")
    public ResponseEntity<?> submitTimesheet(@PathVariable String timesheetId) {
        try {
            System.out.println("Received submit request for Timesheet ID: " + timesheetId);

            // Find the timesheet
            Timesheets timesheet = timesheetService.findByTimesheetId(timesheetId)
                    .orElseThrow(() -> new RuntimeException("Timesheet not found with ID: " + timesheetId));

            // Validate timesheet status is DRAFT
            if (!timesheet.getStatus().equals(TimesheetStatus.DRAFT)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Only DRAFT timesheets can be submitted");
            }

            // Validate all projects are in DRAFT status
            if (!timesheetService.isValidForSubmission(timesheet)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Cannot submit timesheet with non-DRAFT projects");
            }

            // Submit the timesheet
            Timesheets submittedTimesheet = timesheetService.updateTimesheetStatus(timesheet,
                    TimesheetStatus.SUBMITTED);

            return ResponseEntity.ok().body(
                    Map.of(
                            "message", "Timesheet submitted successfully",
                            "timesheetId", submittedTimesheet.getTimesheetId(),
                            "status", submittedTimesheet.getStatus()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error submitting timesheet: " + e.getMessage());
        }
    }

    @PutMapping("/submit-all")
    public ResponseEntity<?> submitAllDraftTimesheets() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            OurUsers user = (OurUsers) authentication.getPrincipal();
            String empId = user.getEmpId();

            // Find all draft timesheets for this employee
            List<Timesheets> draftTimesheets = timesheetService.findByEmployeeIdAndStatus(empId, TimesheetStatus.DRAFT);

            if (draftTimesheets.isEmpty()) {
                return ResponseEntity.ok().body("No draft timesheets found");
            }

            // Validate all projects in each timesheet are also in DRAFT status
            List<String> invalidTimesheets = draftTimesheets.stream()
                    .filter(timesheet -> !timesheetService.isValidForSubmission(timesheet))
                    .map(Timesheets::getTimesheetId)
                    .collect(Collectors.toList());

            if (!invalidTimesheets.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "message", "Cannot submit timesheets with non-DRAFT projects",
                                "invalidTimesheets", invalidTimesheets));
            }

            // Submit all valid draft timesheets
            List<Timesheets> submittedTimesheets = timesheetService.updateTimesheetsStatus(draftTimesheets,
                    TimesheetStatus.SUBMITTED);

            return ResponseEntity.ok().body(
                    Map.of(
                            "message", "Successfully submitted " + submittedTimesheets.size() + " timesheets",
                            "submittedTimesheets", submittedTimesheets.stream()
                                    .map(t -> t.getTimesheetId())
                                    .collect(Collectors.toList())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error submitting timesheets: " + e.getMessage());
        }
    }

    @GetMapping("/manager/timesheets")
    public ResponseEntity<List<TimesheetDto>> getTimesheetsForManagerProjects(
            @RequestParam(required = false) String weekStartDate,
            @RequestParam(required = false) TimesheetStatus status) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String managerId = user.getEmpId();

        System.out.println("🔍 Fetching timesheets for manager with ID: " + managerId);

        // 1. Get all projects where this user is manager
        List<Project> managerProjects = projectService.getProjectsByManagerId(managerId);
        System.out.println("📋 Found " + managerProjects.size() + " projects managed by this user");

        if (managerProjects.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 2. Get all project IDs
        List<Long> projectIds = managerProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        // 3. Get timesheets for these projects
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (weekStartDate != null && !weekStartDate.isEmpty()) {
            startDate = LocalDate.parse(weekStartDate);
            endDate = startDate.with(DayOfWeek.SUNDAY);
        }

        List<TimesheetDto> timesheets = timesheetService.getTimesheetsByProjectIds(projectIds, startDate, endDate,
                status);

        System.out.println("✅ Found " + timesheets.size() + " timesheets for manager's projects");
        return ResponseEntity.ok(timesheets);
    }

    @GetMapping("/manager/{timesheetId}")
    public ResponseEntity<TimesheetDto> getTimesheetForManager(
            @PathVariable String timesheetId,
            @RequestParam(required = false) Long projectId) {

        System.out.println("=== Entering getTimesheetForManager API ===");
        System.out.println("Received request for timesheetId: " + timesheetId);
        System.out.println("Project ID filter: " + projectId);

        // Get current authenticated user (manager)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String managerId = user.getEmpId();

        System.out.println("🔍 Authenticated manager ID: " + managerId);

        // 1. Get the timesheet
        TimesheetDto fullTimesheet = timesheetService.getTimesheetById(timesheetId);
        if (fullTimesheet == null) {
            System.out.println("Timesheet not found for ID: " + timesheetId);
            return ResponseEntity.notFound().build();
        }

        // 2. Get all projects managed by this manager
        List<Project> managerProjects = projectService.getProjectsByManagerId(managerId);
        List<Long> managerProjectIds = managerProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        System.out.println("📋 Manager's project IDs: " + managerProjectIds);

        // 3. Filter the timesheet's projects based on:
        // - Projects managed by this manager
        // - Optional projectId filter
        List<ProjectEntryDto> filteredProjects = fullTimesheet.getProjects().stream()
                .filter(project -> managerProjectIds.contains(project.getProjectId()))
                .filter(project -> projectId == null || project.getProjectId().equals(projectId))
                .collect(Collectors.toList());

        if (filteredProjects.isEmpty()) {
            System.out.println("No matching projects found for the manager and filters");
            return ResponseEntity.notFound().build();
        }

        // Create a new DTO with only the filtered projects
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

        System.out.println("✅ Returning filtered timesheet with " + filteredProjects.size() + " projects");
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/{timesheetId}/projects/{projectId}/status")
    public ResponseEntity<?> updateProjectStatus(
            @PathVariable String timesheetId,
            @PathVariable Long projectId,
            @RequestParam TimesheetStatus status,
            @RequestParam(required = false) String rejectionReason) {

        try {
            // Verify manager permissions
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            OurUsers manager = (OurUsers) authentication.getPrincipal();

            if (!projectService.isManagerOfProject(manager.getEmpId(), projectId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You are not authorized to update this project's status");
            }

            // Update project status and determine new timesheet status
            TimesheetStatus newTimesheetStatus = timesheetService.updateProjectAndTimesheetStatus(
                    timesheetId,
                    projectId,
                    status,
                    rejectionReason);

            // Return updated timesheet
            TimesheetDto updatedDto = timesheetService.getTimesheetById(timesheetId);

            return ResponseEntity.ok(Map.of(
                    "message", "Project status updated successfully",
                    "timesheet", updatedDto,
                    "newTimesheetStatus", newTimesheetStatus));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating project status: " + e.getMessage());
        }
    }

    @PutMapping("/{timesheetId}")
    public ResponseEntity<TimesheetDto> updateTimesheet(@PathVariable String timesheetId,
            @RequestBody TimesheetDto dto) {
        System.out.println("Received update request for Timesheet ID: " + timesheetId);
        System.out.println("Incoming DTO: " + dto);

        TimesheetDto updated = timesheetService.updateTimesheet(timesheetId, dto);

        System.out.println("Updated Timesheet DTO: " + updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/non-drafts")
    public ResponseEntity<List<TimesheetDto>> getAllNonDraftTimesheets() {
        System.out.println("=== Fetching all non-DRAFT timesheets ===");

        // Get all timesheets excluding DRAFT status
        List<TimesheetDto> timesheets = timesheetService.getAllTimesheetsExcludingDrafts();

        System.out.println("✅ Found " + timesheets.size() + " non-DRAFT timesheets");
        return ResponseEntity.ok(timesheets);
    }

    @GetMapping("/non-drafts/{timesheetId}")
    public ResponseEntity<TimesheetDto> getNondraftTimesheetById(
            @PathVariable String timesheetId,
            @RequestParam(required = false) Long projectId) {

        System.out.println("=== Fetching non-draft timesheet by ID ===");
        System.out.println("Timesheet ID: " + timesheetId);
        System.out.println("Project ID: " + projectId);

        // Get the full timesheet data
        TimesheetDto fullTimesheet = timesheetService.getTimesheetById(timesheetId);

        if (fullTimesheet == null) {
            System.out.println("❌ Timesheet not found for ID: " + timesheetId);
            return ResponseEntity.notFound().build();
        }

        // Check if timesheet is DRAFT (shouldn't be accessible via this endpoint)
        if (fullTimesheet.getStatus() == TimesheetStatus.DRAFT) {
            System.out.println("❌ Requested timesheet is in DRAFT status");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }

        // If no project ID specified, return the full timesheet
        if (projectId == null) {
            System.out.println("✅ Returning full non-draft timesheet with ID: " + timesheetId);
            return ResponseEntity.ok(fullTimesheet);
        }

        // Filter projects to only include the requested project
        List<ProjectEntryDto> filteredProjects = fullTimesheet.getProjects().stream()
                .filter(project -> project.getProjectId().equals(projectId))
                .collect(Collectors.toList());

        if (filteredProjects.isEmpty()) {
            System.out.println("❌ Project not found in timesheet: " + projectId);
            return ResponseEntity.notFound().build();
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

        System.out.println("✅ Returning filtered non-draft timesheet with project ID: " + projectId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/reporting-manager/timesheets")
    public ResponseEntity<List<TimesheetDto>> getTimesheetsForReportingManager(
            @RequestParam(required = false) String weekStartDate,
            @RequestParam(required = false) TimesheetStatus status) {

        // Get current authenticated user (manager)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String managerId = user.getEmpId();

        System.out.println("🔍 Fetching timesheets for reporting manager with ID: " + managerId);

        // Create final variables for use in lambda
        final LocalDate filterStartDate;
        final LocalDate filterEndDate;

        // Apply date filter if provided
        if (weekStartDate != null && !weekStartDate.isEmpty()) {
            filterStartDate = LocalDate.parse(weekStartDate);
            filterEndDate = filterStartDate.with(DayOfWeek.SUNDAY);
            System.out.println("📅 Applying date filter: " + filterStartDate + " to " + filterEndDate);
        } else {
            filterStartDate = null;
            filterEndDate = null;
        }

        // Get all timesheets for employees reporting to this manager
        List<TimesheetDto> timesheets = timesheetService.getTimesheetsByReportingManager(managerId);

        // Apply additional filters
        List<TimesheetDto> filteredTimesheets = timesheets.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> filterStartDate == null ||
                        (!t.getWeekStartDate().isBefore(filterStartDate)
                                && !t.getWeekStartDate().isAfter(filterEndDate)))
                .collect(Collectors.toList());

        System.out.println("✅ Found " + filteredTimesheets.size() + " timesheets for reporting manager");
        return ResponseEntity.ok(filteredTimesheets);
    }

    @GetMapping("/{timesheetId}/projects/{projectId}")
    public ResponseEntity<TimesheetDto> getTimesheetByTimesheetIdAndProjectId(
            @PathVariable String timesheetId,
            @PathVariable Long projectId) {

        System.out.println("=== Fetching timesheet by ID and project ID ===");
        System.out.println("Timesheet ID: " + timesheetId);
        System.out.println("Project ID: " + projectId);

        try {
            TimesheetDto timesheet = timesheetService.getTimesheetByTimesheetIdAndProjectId(timesheetId, projectId);
            System.out.println("✅ Found timesheet with the specified project");
            return ResponseEntity.ok(timesheet);
        } catch (RuntimeException e) {
            System.out.println("❌ Error: " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/employees/with-draft-timesheets")
    public ResponseEntity<List<UserDTO>> getUsersWithDraftTimesheets() {
        System.out.println("##### [API] GET /employees/with-draft-timesheets called");
        List<UserDTO> users = timesheetService.getUsersWithPendingTimesheetsForCurrentWeek();
        System.out.println("##### [API] Returning " + users.size() + " users with draft timesheets");
        return ResponseEntity.ok(users);
    }
}
