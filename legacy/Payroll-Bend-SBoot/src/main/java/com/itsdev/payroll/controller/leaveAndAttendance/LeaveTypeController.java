package com.itsdev.payroll.controller.leaveAndAttendance;

import com.itsdev.payroll.dto.leaveAndAttendance.LeaveTypeDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.LeaveTypeResponse;
import com.itsdev.payroll.service.leaveAndAttendance.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    // ===== Create =====
    @PostMapping
    public ResponseEntity<LeaveTypeResponse> createLeaveType(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody LeaveTypeDTO dto) {
        LeaveTypeDTO saved = leaveTypeService.createLeaveTypeForOrg(organizationId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LeaveTypeResponse(0, "Leave Type created successfully.", saved));
    }

    // ===== Update =====
    @PutMapping("/{leaveTypeId}")
    public ResponseEntity<LeaveTypeResponse> updateLeaveType(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long leaveTypeId,
            @Valid @RequestBody LeaveTypeDTO dto) {
        LeaveTypeDTO updated = leaveTypeService.updateLeaveTypeForOrg(organizationId, leaveTypeId, dto);
        return ResponseEntity.ok(new LeaveTypeResponse(0, "Leave Type updated successfully.", updated));
    }

    // ===== Get one =====
    @GetMapping("/{leaveTypeId}")
    public ResponseEntity<LeaveTypeResponse> getLeaveType(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long leaveTypeId) {
        LeaveTypeDTO leaveType = leaveTypeService.getLeaveTypeForOrg(organizationId, leaveTypeId);
        return ResponseEntity.ok(new LeaveTypeResponse(0, "Leave Type fetched successfully.", leaveType));
    }

    // ===== Get all =====
    @GetMapping
    public ResponseEntity<LeaveTypeResponse> getAllLeaveTypes(
            @RequestHeader("organizationId") String organizationId) {
        List<LeaveTypeDTO> leaveTypes = leaveTypeService.getAllLeaveTypesForOrg(organizationId);
        return ResponseEntity.ok(new LeaveTypeResponse(0, "Leave Types fetched successfully.", leaveTypes));
    }

    // ===== Delete (soft delete) =====
    @DeleteMapping("/{leaveTypeId}")
    public ResponseEntity<Void> deleteLeaveType(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long leaveTypeId) {
        leaveTypeService.deleteLeaveTypeForOrg(organizationId, leaveTypeId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ===== Bulk upload/import =====
    @PostMapping("/imports")
    public ResponseEntity<LeaveTypeResponse> bulkUploadLeaveTypes(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody List<LeaveTypeDTO> leaveTypes) {
        leaveTypeService.saveAll(organizationId, leaveTypes);
        List<LeaveTypeDTO> saved = leaveTypeService.getAllLeaveTypesForOrg(organizationId);
        return ResponseEntity.ok(new LeaveTypeResponse(0, "Leave Types saved successfully.", saved));
    }

    // ===== Status update =====
    @PatchMapping("/{leaveTypeId}/status")
    public ResponseEntity<LeaveTypeResponse> updateLeaveTypeStatus(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long leaveTypeId,
            @RequestBody(required = true) java.util.Map<String, String> request) {

        String status = request.get("status");
        LeaveTypeDTO updated = leaveTypeService.updateLeaveTypeStatus(organizationId, leaveTypeId, status);

        return ResponseEntity.ok(new LeaveTypeResponse(0, "Leave Type status updated successfully.", updated));
    }
}
