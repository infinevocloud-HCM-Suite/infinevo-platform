package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.AttendanceDTO;
import com.phegondev.usersmanagementsystem.dto.AttendanceRequest;
import com.phegondev.usersmanagementsystem.entity.Attendance;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.service.AttendanceService;
import com.phegondev.usersmanagementsystem.service.EmployeeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeService employeeService;

    /*
    @PostMapping("/attendance/clock-in")
    public ResponseEntity<Attendance> clockIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();

        System.out.println("🟢 Clock-In Request by user: " + user.getEmpId() + " - " + user.getName());

        return ResponseEntity.ok(service.clockIn(user.getEmpId(), user.getName()));
    } */
/*
    @PostMapping("/attendance/clock-in")
    public ResponseEntity<AttendanceDTO> clockIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();

        System.out.println("🟢 Clock-In Request by user: " + user.getEmpId() + " - " + user.getName());

        AttendanceDTO attendanceDTO = attendanceService.clockIn(user.getEmpId(), user.getName());

        return ResponseEntity.ok(attendanceDTO);
    } */

    @PostMapping("/attendance/clock-in")
    public ResponseEntity<AttendanceDTO> clockIn(@RequestBody AttendanceRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();

        System.out.println("🟢 Clock-In Request by user: " + user.getEmpId() + " - " + user.getName());

        // Convert date + time → LocalDateTime
        LocalDate localDate = LocalDate.parse(request.getDate()); // "2025-08-21"
        LocalTime localTime = LocalTime.parse(request.getTime()); // "10:20:42"
        LocalDateTime now = LocalDateTime.of(localDate, localTime);

        System.out.println("⏰ Parsed Clock-In Time: " + now);

        AttendanceDTO attendanceDTO = attendanceService.clockIn(user.getEmpId(), user.getName(), now);

        return ResponseEntity.ok(attendanceDTO);
    }


    @GetMapping("/attendance/my-attendance")
    public ResponseEntity<List<AttendanceDTO>> getMyAttendance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();

        System.out.println("📥 [API] GET /attendance/my-attendance called by: " + user.getEmpId() + " - " + user.getName());

        List<AttendanceDTO> dtos = attendanceService.getMyAttendance(user.getEmpId());

        System.out.println("📤 [API] Returning " + dtos.size() + " attendance records for user: " + user.getEmpId());

        return ResponseEntity.ok(dtos);
    }



/*

    @PutMapping("/attendance/clock-out")
    public ResponseEntity<Attendance> clockOut() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();
        List<Attendance> open = service.getOpenAttendance(user.getEmpId());
        if (open.isEmpty()) return ResponseEntity.badRequest().build();
        return service.clockOut(open.get(0).getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attendance/all")
    public ResponseEntity<List<Attendance>> getAll(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (employeeId != null && date != null) {
            return ResponseEntity.ok(service.getByEmployeeAndDateRange(employeeId, date, date));
        } else if (employeeId != null) {
            return ResponseEntity.ok(service.getByEmployeeId(employeeId));
        } else if (date != null) {
            return ResponseEntity.ok(service.getByDateRange(date, date));
        }
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/attendance/my-attendance")
    public ResponseEntity<List<Attendance>> getMyAttendance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();
        return ResponseEntity.ok(service.getByEmployeeId(user.getEmpId()));
    }

    @GetMapping("/attendance/date-range")
    public ResponseEntity<List<Attendance>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.getByDateRange(startDate, endDate));
    }

    @GetMapping("/attendance/today")
    public ResponseEntity<List<Attendance>> getTodayAttendance() {
        return ResponseEntity.ok(service.getTodayAttendance());
    } */

    @GetMapping("/attendance/my-reporting-team")
public ResponseEntity<List<AttendanceDTO>> getMyTeamAttendance() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    OurUsers user = (OurUsers) authentication.getPrincipal();
    String managerId = user.getEmpId();

    System.out.println("🔍 Fetching attendance for team members reporting to manager with ID: " + managerId);

    List<String> employeeIds = employeeService.getEmployeeIdsByReportingManager(managerId);

    if (employeeIds.isEmpty()) {
        System.out.println("ℹ️ No team members found for manager: " + managerId);
        return ResponseEntity.ok(List.of());
    }

    List<AttendanceDTO> teamAttendance = attendanceService.getByEmployeeIds(employeeIds);

    System.out.println("✅ Found " + teamAttendance.size() + " attendance records for the team");

    return ResponseEntity.ok(teamAttendance);
}


    @PutMapping("/attendance/{attendanceId}/clock-out")
    public ResponseEntity<AttendanceDTO> clockOut(@PathVariable Long attendanceId, @RequestBody AttendanceRequest request) {
        System.out.println("🚨 [API] Clock-Out Request Received");
        System.out.println("🆔 Attendance ID: " + attendanceId);

        // Convert date + time → LocalDateTime
        LocalDate localDate = LocalDate.parse(request.getDate()); // "2025-08-21"
        LocalTime localTime = LocalTime.parse(request.getTime()); // "10:20:42"
        LocalDateTime now = LocalDateTime.of(localDate, localTime);

        System.out.println("⏰ Parsed Clock-In Time: " + now);

        AttendanceDTO dto = attendanceService.clockOutByAttendanceId(attendanceId, now);

        System.out.println("✅ [API] Clock-Out Successful for Attendance ID: " + attendanceId);
        System.out.println("📤 Returning AttendanceDTO with " + dto.getClockSessions().size() + " session(s)");

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/attendance/end-day")
    public ResponseEntity<String> endDay(@RequestBody AttendanceRequest request) {
        System.out.println("🌐 API Triggered: POST /attendance/end-day");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) auth.getPrincipal();

        System.out.println("🔐 Authenticated User - EmpID: " + user.getEmpId() + ", Name: " + user.getName());


        // Convert date + time → LocalDateTime
        LocalDate localDate = LocalDate.parse(request.getDate()); // "2025-08-21"
        LocalTime localTime = LocalTime.parse(request.getTime()); // "10:20:42"
        LocalDateTime now = LocalDateTime.of(localDate, localTime);

        System.out.println("⏰ Parsed Clock-In Time: " + now);
        attendanceService.endCurrentDay(user.getEmpId(), now);

        System.out.println("📤 Response: Day ended successfully for employee: " + user.getEmpId());

        return ResponseEntity.ok("✅ Day ended successfully. Next clock-in will create a new attendance record.");
    }



    @GetMapping("/attendance/all")
    public ResponseEntity<List<AttendanceDTO>> getAll(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (employeeId != null && date != null) {
            return ResponseEntity.ok(attendanceService.getByEmployeeAndDateRange(employeeId, date, date));
        } else if (employeeId != null) {
            return ResponseEntity.ok(attendanceService.getByEmployeeId(employeeId));
        } else if (date != null) {
            return ResponseEntity.ok(attendanceService.getByDateRange(date, date));
        }
        return ResponseEntity.ok(attendanceService.getAll());
    }




}