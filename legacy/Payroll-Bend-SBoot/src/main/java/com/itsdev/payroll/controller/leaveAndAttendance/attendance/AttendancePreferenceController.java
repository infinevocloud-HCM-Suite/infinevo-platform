package com.itsdev.payroll.controller.leaveAndAttendance.attendance;

import com.itsdev.payroll.dto.leaveAndAttendance.attendance.AttendancePreferenceDTO;
import com.itsdev.payroll.service.leaveAndAttendance.attendance.AttendancePreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance-preferences")
public class AttendancePreferenceController {

    private final AttendancePreferenceService attendancePreferenceService;

    public AttendancePreferenceController(AttendancePreferenceService attendancePreferenceService) {
        this.attendancePreferenceService = attendancePreferenceService;
    }

    // Create Attendance Preference
    @PostMapping
    public ResponseEntity<AttendancePreferenceDTO> createAttendancePreference(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody AttendancePreferenceDTO dto) {
        return ResponseEntity.ok(attendancePreferenceService.createAttendancePreference(organizationId, dto));
    }

    // Update Attendance Preference
    @PutMapping("/{preferenceId}")
    public ResponseEntity<AttendancePreferenceDTO> updateAttendancePreference(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long preferenceId,
            @RequestBody AttendancePreferenceDTO dto) {
        return ResponseEntity.ok(attendancePreferenceService.updateAttendancePreference(organizationId, preferenceId, dto));
    }

    // Delete Attendance Preference
    @DeleteMapping("/{preferenceId}")
    public ResponseEntity<Void> deleteAttendancePreference(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long preferenceId) {
        attendancePreferenceService.deleteAttendancePreference(organizationId, preferenceId);
        return ResponseEntity.noContent().build();
    }

    // Get Attendance Preference by ID
    @GetMapping("/{preferenceId}")
    public ResponseEntity<AttendancePreferenceDTO> getAttendancePreference(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long preferenceId) {
        return ResponseEntity.ok(attendancePreferenceService.getAttendancePreference(organizationId, preferenceId));
    }

    // Get All Attendance Preferences
    @GetMapping
    public ResponseEntity<List<AttendancePreferenceDTO>> getAllAttendancePreferences(
            @RequestHeader("organizationId") String organizationId) {
        return ResponseEntity.ok(attendancePreferenceService.getAllAttendancePreferences(organizationId));
    }
}
