package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.service.employee.EmployeeInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee-invitations")
public class EmployeeInvitationController {

    private final EmployeeInvitationService invitationService;

    public EmployeeInvitationController(EmployeeInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createInvitation(@RequestHeader("organizationId") String orgId,
                                                                @RequestBody EmployeeInvitationDTO dto) {
        EmployeeInvitationDTO saved = invitationService.createInvitation(orgId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Employee invitation created successfully");
        response.put("data", saved);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{invitationId}")
    public ResponseEntity<Map<String, Object>> updateInvitation(@RequestHeader("organizationId") String orgId,
                                                                @PathVariable String invitationId,
                                                                @RequestBody EmployeeInvitationDTO dto) {
        EmployeeInvitationDTO updated = invitationService.updateInvitation(orgId, invitationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee invitation updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInvitations(@RequestHeader("organizationId") String orgId) {
        List<EmployeeInvitationDTO> invitations = invitationService.getInvitations(orgId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee invitations fetched successfully");
        response.put("data", invitations);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{invitationId}")
    public ResponseEntity<Map<String, Object>> getInvitation(@RequestHeader("organizationId") String orgId,
                                                             @PathVariable String invitationId) {
        EmployeeInvitationDTO invitation = invitationService.getInvitation(orgId, invitationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee invitation fetched successfully");
        response.put("data", invitation);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{invitationId}/accept")
    public ResponseEntity<Map<String, Object>> acceptInvitation(@RequestHeader("organizationId") String orgId,
                                                                @PathVariable String invitationId) {
        EmployeeInvitationDTO accepted = invitationService.acceptInvitation(orgId, invitationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee invitation accepted successfully");
        response.put("data", accepted);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<Map<String, Object>> getInvitationByEmployeeId(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        EmployeeInvitationDTO invitation = invitationService.findByEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for employeeId: " + employeeId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation fetched successfully");
        response.put("data", invitation);

        return ResponseEntity.ok(response);
    }


}
