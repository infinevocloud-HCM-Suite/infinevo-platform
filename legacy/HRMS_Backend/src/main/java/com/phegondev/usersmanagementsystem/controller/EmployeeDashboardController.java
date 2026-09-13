package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.timesheet.ProjectEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TaskEntryDto;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetDto;
import com.phegondev.usersmanagementsystem.entity.*;
import com.phegondev.usersmanagementsystem.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.phegondev.usersmanagementsystem.service.timesheet.*;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetService;
import com.phegondev.usersmanagementsystem.serviceimpl.timeshhet.TimesheetServiceImpl;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/employee-dashboard")
public class EmployeeDashboardController {

    private final AttendanceService attendanceService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final LeaveBalanceService leaveBalanceService;
    private final TimesheetServiceImpl timesheetService;

    public EmployeeDashboardController(AttendanceService attendanceService,
                                     ProjectService projectService,
                                     TaskService taskService,
                                     LeaveBalanceService leaveBalanceService,
                                     TimesheetService timesheetService) {
        this.attendanceService = attendanceService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.leaveBalanceService = leaveBalanceService;
        this.timesheetService = (TimesheetServiceImpl) timesheetService;
    }

    private String getCurrentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();
        return user.getEmpId();
    }

    @GetMapping("/dashboard-data")
    public ResponseEntity<Map<String, Object>> getDashboardData() {
        String empId = getCurrentEmployeeId();
        Map<String, Object> dashboardData = new HashMap<>();

        // Projects
        dashboardData.put("projects", projectService.getProjectsByEmpId(empId));

        // Tasks
        dashboardData.put("tasks", taskService.getAllUsersTasks(empId));

        // Attendance
        dashboardData.put("attendance", attendanceService.getByEmployeeId(empId));

        // Current Attendance
        // List<Attendance> openAttendance = attendanceService.getOpenAttendance(empId);
        // if (!openAttendance.isEmpty()) {
        //     dashboardData.put("currentAttendance", openAttendance.get(0));
        // }

        // Leave Balance
        dashboardData.put("leaveBalance", leaveBalanceService.getOrCreateLeaveBalance(empId));

        // Timesheets - Modified to use the new timesheet service
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());
        
        List<TimesheetDto> timesheets = timesheetService.getTimesheetsByEmployeeAndWeek(
            empId, 
            startOfMonth, 
            endOfMonth
        );
        
        // Format timesheets for the dashboard
        List<Map<String, Object>> formattedTimesheets = timesheets.stream()
            .map(ts -> {
                Map<String, Object> tsMap = new HashMap<>();
                tsMap.put("id", ts.getTimesheetId());
                tsMap.put("status", ts.getStatus().toString());
                tsMap.put("createdAt", ts.getCreatedAt());
                
                // Get first project and task for display
                if (!ts.getProjects().isEmpty()) {
                    ProjectEntryDto project = ts.getProjects().get(0);
                    tsMap.put("projectName", project.getProjectName());
                    
                    if (!project.getTasks().isEmpty()) {
                        TaskEntryDto task = project.getTasks().get(0);
                        tsMap.put("taskName", task.getTaskName());
                    }
                }
                
                return tsMap;
            })
            .collect(Collectors.toList());
            
        dashboardData.put("timesheets", formattedTimesheets);

        return ResponseEntity.ok(dashboardData);
    }

    /* Other endpoints remain the same */
    // @PostMapping("/check-in")
    // public ResponseEntity<Attendance> checkIn() {
    //     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    //     OurUsers user = (OurUsers) auth.getPrincipal();
    //     return ResponseEntity.ok(attendanceService.clockIn(user.getEmpId(), user.getName()));
    // }

    // @PostMapping("/check-out")
    // public ResponseEntity<Attendance> checkOut() {
    //     String empId = getCurrentEmployeeId();
    //     List<Attendance> open = attendanceService.getOpenAttendance(empId);
    //     if (open.isEmpty()) {
    //         return ResponseEntity.badRequest().build();
    //     }
    //     return attendanceService.clockOut(open.get(0).getId())
    //             .map(ResponseEntity::ok)
    //             .orElse(ResponseEntity.notFound().build());
    // }
}