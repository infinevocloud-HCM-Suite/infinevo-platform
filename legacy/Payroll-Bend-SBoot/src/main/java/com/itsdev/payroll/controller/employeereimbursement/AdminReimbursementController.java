package com.itsdev.payroll.controller.employeereimbursement;

import com.itsdev.payroll.dto.employeereimbursement.AdminReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.ApproveReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.RejectReimbursementRequestDTO;
import com.itsdev.payroll.service.employeereimbursement.EmployeeReimbursementService;
import com.itsdev.payroll.util.JWTUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Admin Reimbursement management and approval workflow.
 *
 * Base URLs supported:
 *   - /admin/reimbursements
 *   - /api/admin/reimbursements
 *
 * Security:
 *   - Protected by Spring Security and OrganizationRoleInterceptor (Admin role required).
 *   - Admin ID is extracted from JWT token subject (never from request body).
 *   - Multi-tenant isolation enforced via organizationId request header.
 */
@RestController
@RequestMapping({"/admin/reimbursements", "/api/admin/reimbursements"})
public class AdminReimbursementController {

    private static final Logger log = LoggerFactory.getLogger(AdminReimbursementController.class);

    private final EmployeeReimbursementService reimbursementService;

    public AdminReimbursementController(EmployeeReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    // ====================== 1. GET ALL REIMBURSEMENTS ======================

    /**
     * Fetch all employee reimbursement requests within the organization.
     *
     * GET /admin/reimbursements
     *
     * @param organizationId from request header
     * @return 200 OK with list of Admin reimbursement DTOs
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAdminReimbursements(
            @RequestHeader("organizationId") String organizationId) {

        String method = "getAdminReimbursements";
        log.info("[{}] 📥 Incoming request to fetch reimbursements | organizationId={}",
                method, organizationId);

        List<AdminReimbursementResponseDTO> reimbursements = reimbursementService.getAdminReimbursements(organizationId);

        log.info("[{}] ✅ Successfully fetched {} reimbursements", method, reimbursements.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursements fetched successfully");
        response.put("data", reimbursements);

        log.info("[{}] 📤 Response sent to client", method);

        return ResponseEntity.ok(response);
    }

    // ====================== 2. GET REIMBURSEMENT BY ID ======================

    /**
     * Fetch complete details of a single reimbursement request by ID.
     *
     * GET /admin/reimbursements/{id}
     *
     * @param id             reimbursement request ID
     * @param organizationId from request header
     * @return 200 OK with reimbursement details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAdminReimbursementById(
            @PathVariable Long id,
            @RequestHeader("organizationId") String organizationId) {

        String method = "getAdminReimbursementById";
        log.info("[{}] 📥 Incoming request for reimbursement details | id={} | organizationId={}",
                method, id, organizationId);

        AdminReimbursementResponseDTO details = reimbursementService.getAdminReimbursementById(id, organizationId);

        log.info("[{}] ✅ Reimbursement details fetched | reimbursementId={}", method, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement fetched successfully");
        response.put("data", details);

        log.info("[{}] 📤 Response sent to client", method);

        return ResponseEntity.ok(response);
    }

    // ====================== 3. APPROVE REIMBURSEMENT ======================

    /**
     * Approve a reimbursement request with an approved amount and optional remarks.
     *
     * PUT /admin/reimbursements/{id}/approve
     *
     * @param id             reimbursement request ID
     * @param organizationId from request header
     * @param dto            approval payload with approvedAmount and optional remarks
     * @return 200 OK with updated reimbursement details
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveReimbursement(
            @PathVariable Long id,
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody ApproveReimbursementRequestDTO dto) {

        String method = "approveReimbursement";
        String adminId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        log.info("[{}] 📥 Approval request | reimbursementId={} | organizationId={} | adminId={}",
                method, id, organizationId, adminId);

        AdminReimbursementResponseDTO approved = reimbursementService.approveReimbursement(
                id, organizationId, adminId, dto);

        log.info("[{}] ✅ Reimbursement approved successfully | reimbursementId={} | approvedAmount={}",
                method, id, approved.getApprovedAmount());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement approved successfully");
        response.put("data", approved);

        log.info("[{}] 📤 Response sent to client", method);

        return ResponseEntity.ok(response);
    }

    // ====================== 4. REJECT REIMBURSEMENT ======================

    /**
     * Reject a reimbursement request with rejection remarks.
     *
     * PUT /admin/reimbursements/{id}/reject
     *
     * @param id             reimbursement request ID
     * @param organizationId from request header
     * @param dto            rejection payload with remarks
     * @return 200 OK with updated reimbursement details
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectReimbursement(
            @PathVariable Long id,
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody RejectReimbursementRequestDTO dto) {

        String method = "rejectReimbursement";
        String adminId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        log.info("[{}] 📥 Rejection request | reimbursementId={} | organizationId={} | adminId={}",
                method, id, organizationId, adminId);

        AdminReimbursementResponseDTO rejected = reimbursementService.rejectReimbursement(
                id, organizationId, adminId, dto);

        log.info("[{}] ✅ Reimbursement rejected successfully | reimbursementId={} | remarks={}",
                method, id, rejected.getRemarks());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement rejected successfully");
        response.put("data", rejected);

        log.info("[{}] 📤 Response sent to client", method);

        return ResponseEntity.ok(response);
    }
}
