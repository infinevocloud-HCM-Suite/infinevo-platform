package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.OvertimeRequestDTO;
import com.phegondev.usersmanagementsystem.entity.OvertimeRequest;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.enumuration.OvertimeStatus;
import com.phegondev.usersmanagementsystem.service.OvertimeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
public class OvertimeRequestController {

    @Autowired
    private OvertimeRequestService service;

    @PostMapping("/overtime/add")
    public ResponseEntity<?> createOvertimeRequest(@RequestBody OvertimeRequestDTO dto) {
        System.out.println("[CONTROLLER] Received overtime request payload: " + dto);

        // Validate time
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            System.out.println("[VALIDATION FAILED] End time is before start time");
            return ResponseEntity.badRequest().body("End time cannot be before start time.");
        }

        // Get authenticated user details
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        System.out.println("[AUTH] Authenticated user: " + user.getEmpId() + " - " + user.getName());

        // Populate employee info into the DTO
        dto.setEmployeeId(user.getEmpId());
        dto.setEmployeeName(user.getName());
        System.out.println("[DTO UPDATE] Set employeeId and employeeName in DTO");

        // Save the overtime request
        OvertimeRequest savedRequest = service.save(dto);
        System.out.println("[SERVICE] Overtime request saved with ID: " + savedRequest.getId());

        // Return success response
        return ResponseEntity.ok(savedRequest);
    }


    @GetMapping("/overtime")
    public ResponseEntity<List<OvertimeRequestDTO>> getOvertimeRequestsByEmployeeId() {
        System.out.println("[GET] Request received for logged-in user's overtime list");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();
        System.out.println("[AUTH] Employee ID: " + empId);

        List<OvertimeRequestDTO> dtoList = service.getDtosByEmployeeId(empId);

        if (dtoList.isEmpty()) {
            System.out.println("[RESPONSE] No overtime requests found");
            return ResponseEntity.noContent().build();
        }

        System.out.println("[RESPONSE] Returning " + dtoList.size() + " overtime request DTOs");
        return ResponseEntity.ok(dtoList);
    }


    @GetMapping("/overtime/all")
    public List<OvertimeRequest> getAllOvertimeRequests(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return service.getByStatus(status);
        }
        return service.getAll();
    }

    @GetMapping("/overtime/manager")
    public ResponseEntity<List<OvertimeRequestDTO>> getOvertimeRequestByManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String managerEmployeeId = user.getEmpId();
        System.out.println("[AUTH] Employee ID: " + managerEmployeeId);

        List<OvertimeRequestDTO> requests = service.getOvertimeRequestByManager(managerEmployeeId);
        return ResponseEntity.ok(requests);
    }

@PutMapping("/overtime/{id}/status")
public ResponseEntity<OvertimeRequest> updateStatus(
        @PathVariable Long id,
        @RequestParam String status,
        @RequestParam(required = false) String comment) {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    OurUsers user = (OurUsers) authentication.getPrincipal();

    // Use getRoleNames() instead of single getRole() to support multi-role users
    List<String> roles = user.getRoleNames().stream()
                             .map(String::toLowerCase)
                             .collect(Collectors.toList());

    System.out.println("[API] Overtime status update requested. ID: " + id + ", Status: " + status + ", User empId: " + user.getEmpId() + ", Roles: " + roles + ", Comment: " + comment);

    // Decide acting role: prioritize manager -> reporting manager -> hr (adjust priorities as you need)
    String actingRole = null;
    if (roles.contains("manager")) {
        actingRole = "manager";
    } else if (roles.contains("reporting manager") || roles.contains("reporting_manager") || roles.contains("reporting-manager")) {
        actingRole = "reporting manager";
    } else if (roles.contains("hr")) {
        actingRole = "hr";
    }

    if (actingRole == null) {
        System.out.println("[AUTH] User not authorized to update overtime status. Roles: " + roles);
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
    }

    try {
        // Convert status to enum (case-insensitive)
        OvertimeStatus newStatus = OvertimeStatus.valueOf(status.toUpperCase());

        // Forward actingRole to the service (service likely expects a role string)
        OvertimeRequest updatedRequest = service.updateStatus(id, actingRole, newStatus, comment);
        return ResponseEntity.ok(updatedRequest);

    } catch (IllegalArgumentException e) {
        System.out.println("[ERROR] Invalid status value: " + status + " -> " + e.getMessage());
        return ResponseEntity.badRequest().build();
    } catch (RuntimeException e) {
        System.out.println("[ERROR] " + e.getMessage());
        return ResponseEntity.notFound().build();
    }
}





    @PutMapping("/overtime/{id}")
    public ResponseEntity<OvertimeRequest> updateOvertimeRequest(
            @PathVariable Long id,
            @RequestBody OvertimeRequest requestDetails) {
        try {
            OvertimeRequest updatedRequest = service.updateOvertimeRequest(id, requestDetails);
            return ResponseEntity.ok(updatedRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/overtime/{id}")
    public ResponseEntity<Void> deleteOvertimeRequest(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/overtime/{id}")
    public ResponseEntity<OvertimeRequest> getOvertimeRequestById(
            @PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}