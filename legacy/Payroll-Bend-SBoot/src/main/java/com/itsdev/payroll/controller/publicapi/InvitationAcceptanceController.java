package com.itsdev.payroll.controller.publicapi;

import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.CompanyUserService;
import com.itsdev.payroll.service.employee.EmployeeInvitationService;
import com.itsdev.payroll.service.organization.UserInvitationService;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "*")
public class InvitationAcceptanceController {

    @Autowired
    private UserInvitationService userInvitationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    // NEW: Add this repository
    @Autowired
    private BasicDetailsRepository basicDetailsRepository;

    @Autowired
    private EmployeeInvitationService employeeInvitationService;

    @Autowired
    private CompanyUserService companyUserService;

    @Value("${fed.secret}")
    private String fedSecret;

    private boolean isValidFedSecret(String secretHeader) {
        return secretHeader != null && secretHeader.equals(fedSecret);
    }

    /**
     * =========================
     * GET invitation details
     * =========================
     */

    private static final Logger log = LoggerFactory.getLogger(InvitationAcceptanceController.class);

    @GetMapping("/invitation-by-email")
    public ResponseEntity<?> getInvitationByEmail(
            @RequestHeader("X-Secret-Fed-Code") String secret,
            @RequestParam String user,
            @RequestParam String type,
            @RequestParam String orgId) {

        if (!isValidFedSecret(secret)) {
            return ResponseEntity.status(401).body("Invalid fed secret");
        }

        try {
            log.info("🔍 Fetching invitation: email={}, type={}, orgId={}", user, type, orgId);

            if ("USER".equalsIgnoreCase(type)) {
                UserInvitationDTO invitation = userInvitationService.findByEmailAndOrganization(user, orgId);

                if (invitation == null) {
                    return ResponseEntity.status(404).body("Invitation not found");
                }

                Map<String, Object> response = new HashMap<>();
                response.put("type", "USER");
                response.put("name", invitation.getName() != null ? invitation.getName() : "");
                response.put("email", invitation.getEmail() != null ? invitation.getEmail() : "");
                response.put("role", invitation.getUserRole() != null ? invitation.getUserRole() : "");
                response.put("organization",
                        invitation.getOrganizationId() != null ? invitation.getOrganizationId() : "");
                response.put("status",
                        Boolean.TRUE.equals(invitation.getIsInvitationAccepted()) ? "accepted" : "pending");

                return ResponseEntity.ok(response);
            }

            if ("EMPLOYEE".equalsIgnoreCase(type)) {
                EmployeeInvitationDTO invitation = employeeInvitationService.findByEmailAndOrganization(user, orgId);

                if (invitation == null) {
                    return ResponseEntity.status(404).body("Employee invitation not found");
                }

                if (invitation.getExpiryDate() != null && java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).isAfter(invitation.getExpiryDate())) {
                    return ResponseEntity.badRequest().body("Invitation has expired");
                }

                // NEW: Fetch employee details to get name and designation
                String employeeName = "";
                String employeeRole = "";
                String employeeNumber = "";

                Optional<BasicDetails> employeeOpt = basicDetailsRepository
                        .findByOrganization_OrganizationIdAndWorkMail(orgId, user);

                if (employeeOpt.isPresent()) {
                    BasicDetails employee = employeeOpt.get();

                    // Build employee name
                    StringBuilder nameBuilder = new StringBuilder();
                    if (employee.getFirstName() != null) {
                        nameBuilder.append(employee.getFirstName());
                    }
                    if (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()) {
                        nameBuilder.append(" ").append(employee.getMiddleName());
                    }
                    if (employee.getLastName() != null && !employee.getLastName().isEmpty()) {
                        nameBuilder.append(" ").append(employee.getLastName());
                    }
                    employeeName = nameBuilder.toString().trim();

                    // Get employee number
                    employeeNumber = employee.getEmployeeNumber() != null ? employee.getEmployeeNumber() : "";

                    // Get designation/role
                    if (employee.getDesignation() != null && employee.getDesignation().getName() != null) {
                        employeeRole = employee.getDesignation().getName();
                    }
                }

                Map<String, Object> response = new HashMap<>();
                response.put("type", "EMPLOYEE");
                response.put("email", invitation.getEmail() != null ? invitation.getEmail() : "");
                response.put("organization",
                        invitation.getOrganizationId() != null ? invitation.getOrganizationId() : "");
                response.put("status",
                        Boolean.TRUE.equals(invitation.getIsInvitationAccepted()) ? "accepted" : "pending");
                response.put("portalEnabled",
                        Boolean.TRUE.equals(invitation.getIsPortalEnabled()) ? true : false);

                // NEW: Add employee details
                response.put("name", employeeName);
                response.put("role", employeeRole);
                response.put("employeeNumber", employeeNumber);

                return ResponseEntity.ok(response);
            }

            return ResponseEntity.badRequest().body("Invalid type");

        } catch (RuntimeException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    /**
     * =========================
     * ACCEPT / REJECT invitation
     * =========================
     */
    @PostMapping("/process-invitation")
    public ResponseEntity<?> processInvitation(
            @RequestHeader("X-Secret-Fed-Code") String secret,
            @RequestBody ProcessInvitationRequest request) {

        if (!isValidFedSecret(secret)) {
            return ResponseEntity.status(401).body("Invalid fed secret");
        }

        if (request.getUserEmail() == null || request.getOrganizationId() == null) {
            return ResponseEntity.badRequest()
                    .body("userEmail and organizationId are required");
        }

        try {
            if ("USER".equalsIgnoreCase(request.getType())) {
                return processUserByEmail(request);
            }

            if ("EMPLOYEE".equalsIgnoreCase(request.getType())) {
                return processEmployeeByEmail(request);
            }

            return ResponseEntity.badRequest().body("Invalid type");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    private ResponseEntity<?> processUserByEmail(ProcessInvitationRequest request) {
        UserInvitationDTO invitation = userInvitationService.findByEmailAndOrganization(
                request.getUserEmail(),
                request.getOrganizationId());

        if (Boolean.TRUE.equals(invitation.getIsInvitationAccepted())) {
            return ResponseEntity.ok("Already accepted");
        }

        if ("REJECT".equalsIgnoreCase(request.getAction())) {
            userInvitationService.markAsRejected(
                    request.getUserEmail(),
                    request.getOrganizationId(),
                    request.getRejectionReason());
            return ResponseEntity.ok("Invitation rejected");
        }

        if ("ACCEPT".equalsIgnoreCase(request.getAction())) {
            CompanyUserDTO dto = new CompanyUserDTO();
            dto.setUserEmail(invitation.getEmail());
            dto.setRoleId(invitation.getRoleId());
            dto.setRoleName(invitation.getUserRole());
            dto.setOrganizationId(invitation.getOrganizationId());

            companyUserService.assignOrUpdateRoleToUserInOrganization(dto);
            userInvitationService.markAsAccepted(
                    request.getUserEmail(),
                    request.getOrganizationId());

            return ResponseEntity.ok("User invitation accepted");
        }

        return ResponseEntity.badRequest().body("Invalid action");
    }

    private ResponseEntity<?> processEmployeeByEmail(ProcessInvitationRequest request) {
        EmployeeInvitationDTO invitation = employeeInvitationService.findByEmailAndOrganization(
                request.getUserEmail(),
                request.getOrganizationId());

        if (Boolean.TRUE.equals(invitation.getIsInvitationAccepted())) {
            return ResponseEntity.ok("Already accepted");
        }

        if (invitation.getExpiryDate() != null && java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).isAfter(invitation.getExpiryDate())) {
            return ResponseEntity.badRequest().body("Invitation has expired");
        }

        if ("REJECT".equalsIgnoreCase(request.getAction())) {
            employeeInvitationService.markAsRejected(
                    request.getUserEmail(),
                    request.getOrganizationId(),
                    request.getRejectionReason());
            return ResponseEntity.ok("Invitation rejected");
        }

        if ("ACCEPT".equalsIgnoreCase(request.getAction())) {
            CompanyUserDTO dto = new CompanyUserDTO();
            dto.setUserEmail(invitation.getEmail());
            dto.setOrganizationId(invitation.getOrganizationId());
            dto.setIsEmployeePortalEnable(true);

            companyUserService.toggleEmployeePortalAccess(dto);
            employeeInvitationService.markAsAccepted(
                    request.getUserEmail(),
                    request.getOrganizationId());

            return ResponseEntity.ok("Employee invitation accepted");
        }

        return ResponseEntity.badRequest().body("Invalid action");
    }
    /*
     * =========================
     * Request DTO
     * =========================
     */

    static class ProcessInvitationRequest {
        private String token;
        private String type;
        private String userEmail;
        private String organizationId;
        private String action;
        private String rejectionReason;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public void setUserEmail(String userEmail) {
            this.userEmail = userEmail;
        }

        public String getOrganizationId() {
            return organizationId;
        }

        public void setOrganizationId(String organizationId) {
            this.organizationId = organizationId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }
    }
}
