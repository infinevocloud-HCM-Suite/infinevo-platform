package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.CombinedUserDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.service.organization.UserInvitationService;
import com.itsdev.payroll.util.JWTUtil;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invitations")
public class UserInvitationController {

    private final UserInvitationService invitationService;

    public UserInvitationController(UserInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createInvitation(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody UserInvitationDTO dto) {

        UserInvitationDTO created = invitationService.createInvitation(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Invitation created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String userId,
            @Valid @RequestBody UserInvitationDTO dto) {

        UserInvitationDTO updated = invitationService.updateInvitation(organizationId, userId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String userId) {

        UserInvitationDTO invitation = invitationService.getInvitation(organizationId, userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation fetched successfully");
        response.put("data", invitation);

        return ResponseEntity.ok(response);
    }

    // @GetMapping
    // public ResponseEntity<Map<String, Object>> getAllInvitations(
    // @RequestHeader("organizationId") String organizationId) {

    // List<UserInvitationDTO> invitations =
    // invitationService.getAllInvitations(organizationId);

    // Map<String, Object> response = new LinkedHashMap<>();
    // response.put("status", HttpStatus.OK.value());
    // response.put("message", "Invitations fetched successfully");
    // response.put("data", invitations);

    // return ResponseEntity.ok(response);
    // }

    // UPDATED: This endpoint now returns combined users + employees
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestHeader("organizationId") String organizationId) {

        String method = "getAllUsers";

        try {
            // Get current user ID from JWT token
            String currentUserId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

            // Get combined users and employees list
            List<CombinedUserDTO> users = invitationService.getAllUsersWithLoginInfo(organizationId, currentUserId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Users and employees fetched successfully");

            // Create response data with counts
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", users);
            data.put("totalUsers", users.size());

            // Count by type
            long orgUsersCount = users.stream().filter(u -> "ORGANIZATION_USER".equals(u.getUserType())).count();
            long employeesCount = users.stream().filter(u -> "EMPLOYEE".equals(u.getUserType())).count();

            data.put("organizationUsersCount", orgUsersCount);
            data.put("employeesCount", employeesCount);

            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "Failed to fetch users: " + e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String userId) {

        invitationService.deleteInvitation(organizationId, userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/inactive/{userId}")
    public ResponseEntity<Map<String, Object>> inactivateInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String userId) {

        invitationService.inactivateInvitation(organizationId, userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation inactivated successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/active/{userId}")
    public ResponseEntity<Map<String, Object>> reactivateInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String userId) {

        invitationService.reactivateInvitation(organizationId, userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Invitation reactivated successfully");

        return ResponseEntity.ok(response);
    }

}
