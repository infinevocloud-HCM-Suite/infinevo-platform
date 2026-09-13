package com.itsdev.payroll.controller;

import com.itsdev.payroll.controller.employee.BasicDetailsController;
import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.service.CompanyUserService;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.service.BrevoEmailService;

import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class CompanyUserController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private KeycloakUserService keycloakUserService;

    @Autowired
    private BrevoEmailService brevoEmailService;

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;
    
    private static final Logger log = LoggerFactory.getLogger(CompanyUserController.class);

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody CompanyUserDTO userDTO) {
        System.out.println("Received registration request for: " + userDTO.getUserEmail());

        try {
            companyUserService.registerUser(userDTO);
            return ResponseEntity.ok("User registered successfully.");
        } catch (RuntimeException e) {
            System.err.println("Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/organization-user/register")
    public ResponseEntity<Map<String, Object>> registerOrganizationUser(
            @RequestBody CompanyUserDTO userDTO) {

        companyUserService.organizationUserRegistration(userDTO);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Organization user registered successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        List<CompanyUserDTO> users = companyUserService.getAllUsers();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Users fetched successfully");
        response.put("data", users);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-password")
    public ResponseEntity<String> updatePassword(@RequestParam String email,
                                                 @RequestParam String newPassword) {
        keycloakUserService.updateUserPassword(email, newPassword);
        return ResponseEntity.ok("Password updated successfully for " + email);
    }

    @GetMapping("/isEmailExists")
    public ResponseEntity<Map<String, Object>> isEmailExists(@RequestParam String email) {
        boolean isEmailExists = keycloakUserService.isEmailExists(email);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("email", email);
        response.put("isEmailExists", isEmailExists);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestParam String email) {
        boolean emailExists = keycloakUserService.isEmailExists(email);
        Map<String, Object> response = new LinkedHashMap<>();
        
        if (!emailExists) {
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "This email address is not registered in our system.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        // Construct stylized email content with reset password link
        String resetLink = frontendBaseUrl + "/new-password?email=" + email;
        String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;'>" +
                "<div style='text-align: center; margin-bottom: 20px;'>" +
                "<h2 style='color: #3f87f5;'>HRMS InfiNevoCloud</h2>" +
                "<h3>Password Reset Request</h3>" +
                "</div>" +
                "<p>Dear User,</p>" +
                "<p>We received a request to reset your password for your HRMS account. Click the button below to set up a new password:</p>" +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "<a href='" + resetLink + "' style='background-color: #3f87f5; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;'>Proceed to Reset</a>" +
                "</div>" +
                "<p>If you did not make this request, please safely ignore this email.</p>" +
                "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'>" +
                "<p style='font-size: 12px; color: #888; text-align: center;'>This is an automated notification from HRMS Platform.</p>" +
                "</div>";
                
        boolean emailSent = brevoEmailService.sendHtmlEmail(email, "User", "Reset Your HRMS Password", htmlContent);
        
        if (emailSent) {
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Password reset email sent successfully.");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "Failed to send email. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/assign-role")
    public ResponseEntity<Map<String, Object>> assignRoleToUser(@RequestBody CompanyUserDTO dto) {
        companyUserService.assignOrUpdateRoleToUserInOrganization(dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Role assigned/updated successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register-employee")
    public ResponseEntity<Map<String, Object>> registerOrganizationEmployee(
            @RequestBody CompanyUserDTO userDTO) {

        companyUserService.organizationEmployeeRegistration(userDTO);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Organization employee registered successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/toggle-portal-access")
    public ResponseEntity<Map<String, Object>> toggleEmployeePortalAccess(
            @RequestBody CompanyUserDTO dto) {

        companyUserService.toggleEmployeePortalAccess(dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee portal access updated successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/is-temporary")
    public ResponseEntity<Map<String, Object>> isTemporaryUser(@RequestParam String email) {
        try {
            boolean isTemporary = keycloakUserService.isTemporaryUser(email);

            return ResponseEntity.ok(
                    Map.of(
                            "status", "success",
                            "message", "User checked successfully",
                            "data", Map.of(
                                    "email", email,
                                    "isTemporary", isTemporary
                            )
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
    @GetMapping("/hr-users")
    public ResponseEntity<Map<String, Object>> getHrUsers(
            @RequestHeader("organizationId") String organizationId) {

        String method = "getHrUsers";
        log.info("[{}] 📥 Incoming request to fetch HR users | organizationId={}", 
                 method, organizationId);

        List<CompanyUserDTO> users = companyUserService.getHrUsersByOrganization(organizationId);

        log.info("[{}] ✅ Successfully fetched {} HR users", method, users.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "HR users fetched successfully");
        response.put("data", users);

        log.info("[{}] 📤 Response sent to client", method);

        return ResponseEntity.ok(response);
    }



}