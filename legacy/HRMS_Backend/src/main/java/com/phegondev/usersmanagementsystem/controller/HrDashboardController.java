package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.AttendanceDTO;
import com.phegondev.usersmanagementsystem.dto.LeaveRequestsDTO;
import com.phegondev.usersmanagementsystem.entity.Holiday;
import com.phegondev.usersmanagementsystem.enumuration.AttendanceStatus;
import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import com.phegondev.usersmanagementsystem.service.AttendanceService;
import com.phegondev.usersmanagementsystem.service.EmployeeService;
import com.phegondev.usersmanagementsystem.service.HolidayService;
import com.phegondev.usersmanagementsystem.service.leaverequest.LeaveRequestsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/hrdashboard")
public class HrDashboardController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private LeaveRequestsService leaveRequestsService;

    @Autowired
    private HolidayService holidayService;

    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats() {
        Map<String, Object> response = new HashMap<>();

        // Total employees count
        long totalEmployees = employeeService.getAllEmployees().size();
        response.put("employees", totalEmployees);

        // Today's attendance stats
        List<AttendanceDTO> todayAttendance = attendanceService.getTodayAttendance();

        long presentToday = todayAttendance.stream()
                .filter(a -> AttendanceStatus.PRESENT.equals(a.getStatus()))
                .count();

        LocalDate today = LocalDate.now();
        long onLeaveToday = leaveRequestsService.getAllLeaveRequest().stream()
                .filter(lr -> lr.getHrStatus() == LeaveRequestStatus.APPROVED)
                .filter(lr -> !lr.getFromDate().isAfter(today) && !lr.getToDate().isBefore(today))
                .count();

        long absentToday = totalEmployees - presentToday - onLeaveToday;

        response.put("attendanceToday", Map.of(
                "present", presentToday,
                "onLeave", onLeaveToday,
                "absent", absentToday
        ));

        // Pending leave requests count
        long pendingLeaves = leaveRequestsService.getAllLeaveRequest().stream()
                .filter(lr -> lr.getReportingManagerStatus() == LeaveRequestStatus.PENDING ||
                              lr.getHrStatus() == LeaveRequestStatus.PENDING)
                .count();

        response.put("leaves", Map.of("pending", pendingLeaves));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/attendance/recent")
    public ResponseEntity<List<AttendanceDTO>> getRecentAttendance() {
        List<AttendanceDTO> recent = attendanceService.getTodayAttendance().stream()
                .sorted(Comparator.comparing(AttendanceDTO::getInTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(recent);
    }

    @GetMapping("/leaves/current")
    public ResponseEntity<List<LeaveRequestsDTO>> getCurrentLeaves() {
        LocalDate today = LocalDate.now();
        List<LeaveRequestsDTO> currentLeaves = leaveRequestsService.getAllLeaveRequest().stream()
                .filter(lr -> lr.getHrStatus() == LeaveRequestStatus.APPROVED)
                .filter(lr -> !lr.getFromDate().isAfter(today) && !lr.getToDate().isBefore(today))
                .sorted(Comparator.comparing(LeaveRequestsDTO::getFromDate))
                .collect(Collectors.toList());

        return ResponseEntity.ok(currentLeaves);
    }

    @GetMapping("/leaves/upcoming")
    public ResponseEntity<List<LeaveRequestsDTO>> getUpcomingLeaves() {
        LocalDate today = LocalDate.now();
        List<LeaveRequestsDTO> upcoming = leaveRequestsService.getAllLeaveRequest().stream()
                .filter(lr -> lr.getHrStatus() == LeaveRequestStatus.APPROVED)
                .filter(lr -> lr.getFromDate().isAfter(today))
                .sorted(Comparator.comparing(LeaveRequestsDTO::getFromDate))
                .collect(Collectors.toList());

        return ResponseEntity.ok(upcoming);
    }

    @GetMapping("/leaves/recent")
    public ResponseEntity<List<LeaveRequestsDTO>> getRecentDecisions() {
        List<LeaveRequestsDTO> recent = leaveRequestsService.getAllLeaveRequest().stream()
                .filter(lr -> lr.getReportingManagerStatus() != LeaveRequestStatus.PENDING ||
                              lr.getHrStatus() != LeaveRequestStatus.PENDING)
                .sorted(Comparator.comparing(LeaveRequestsDTO::getCreatedDate).reversed())
                .limit(10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(recent);
    }

    @GetMapping("/holidays/upcoming")
    public ResponseEntity<List<Holiday>> getUpcomingHolidays() {
        List<Holiday> holidays = holidayService.getUpcomingHolidays();
        return ResponseEntity.ok(holidays);
    }

    @GetMapping("/notifications/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentNotifications() {
        // Placeholder for notifications - can be implemented later
        List<Map<String, Object>> notifications = new ArrayList<>();

        // Example notification
        Map<String, Object> notification = new HashMap<>();
        notification.put("id", 1);
        notification.put("message", "System is running normally");
        notification.put("timestamp", LocalDateTime.now().toString());
        notification.put("type", "system");
        notifications.add(notification);

        return ResponseEntity.ok(notifications);
    }
}
