package com.itsdev.payroll.controller.employeereimbursement;

import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementResponseDTO;
import com.itsdev.payroll.service.employeereimbursement.EmployeeReimbursementService;
import com.itsdev.payroll.util.JWTUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Employee Reimbursement Request operations.
 *
 * Base URL: /api/employee/reimbursements
 *
 * Security design:
 *  - employeeId is ALWAYS extracted from the authenticated JWT token (via JWTUtil),
 *    never trusted from the request body or client parameter.
 *  - organizationId is ALWAYS read from the request header.
 *  - No endpoint exposes another employee's data.
 *
 * Response format follows the existing project convention:
 *   { "status": 200/201, "message": "...", "data": ... }
 */
@RestController
@RequestMapping("/api/employee/reimbursements")
public class EmployeeReimbursementController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeReimbursementController.class);

    private final EmployeeReimbursementService reimbursementService;

    public EmployeeReimbursementController(EmployeeReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    // ====================== POST: Submit New Request (Multipart) ======================

    /**
     * Submit a new reimbursement request with multipart attachment file.
     * Handles multipart/form-data requests without @RequestBody to avoid 415 Unsupported Media Type errors.
     *
     * POST /api/employee/reimbursements
     * Content-Type: multipart/form-data
     *
     * Multipart fields supported:
     *   - reimbursementType / type : string (e.g. MEDICAL, TRAVEL, etc.)
     *   - requestedAmount          : number / string (e.g. 5000.00)
     *   - billDate                 : LocalDate / string (YYYY-MM-DD)
     *   - description              : string
     *   - attachment / file / files: MultipartFile (PDF, JPG, JPEG, PNG <= 10MB)
     *
     * @param organizationId from request header
     * @return 201 Created with the created reimbursement DTO
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createReimbursementMultipart(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "reimbursementType", required = false) String reimbursementType,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "requestedAmount", required = false) BigDecimal requestedAmount,
            @RequestParam(value = "billDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        String method = "createReimbursementMultipart";
        String employeeId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        EmployeeReimbursementRequestDTO dto = new EmployeeReimbursementRequestDTO();
        if (reimbursementType != null && !reimbursementType.isBlank()) {
            dto.setReimbursementType(reimbursementType.trim());
        } else if (type != null && !type.isBlank()) {
            dto.setReimbursementType(type.trim());
        }

        if (requestedAmount != null) {
            dto.setRequestedAmount(requestedAmount);
        }

        if (billDate != null) {
            dto.setBillDate(billDate);
        }

        if (description != null && !description.isBlank()) {
            dto.setDescription(description.trim());
        }

        // Collect ALL uploaded files from all three params into one list
        List<MultipartFile> allFiles = new ArrayList<>();
        if (files != null) {
            files.stream().filter(f -> f != null && !f.isEmpty()).forEach(allFiles::add);
        }
        // Add standalone "attachment" / "file" params only if NOT already covered by "files"
        if (allFiles.isEmpty()) {
            if (attachment != null && !attachment.isEmpty()) allFiles.add(attachment);
            else if (file != null && !file.isEmpty()) allFiles.add(file);
        }

        log.info("[{}] 📥 Incoming multipart reimbursement request | employeeId={} | organizationId={} | type={} | amount={} | fileCount={}",
                method, employeeId, organizationId, dto.getReimbursementType(), dto.getRequestedAmount(), allFiles.size());

        allFiles.forEach(f -> log.info("[{}] Attachment | filename={} | contentType={} | size={} bytes",
                method, f.getOriginalFilename(), f.getContentType(), f.getSize()));

        EmployeeReimbursementResponseDTO created = reimbursementService.createReimbursement(
                employeeId, organizationId, dto, allFiles);

        log.info("[{}] ✅ Reimbursement created successfully | reimbursementId={} | attachmentUrls={}",
                method, created.getId(), created.getAttachmentUrls());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Reimbursement request submitted successfully.");
        response.put("data", created);

        log.info("[{}] 📤 Response sent successfully | reimbursementId={}", method, created.getId());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ====================== POST: Submit New Request (JSON Fallback) ======================

    /**
     * Submit a new reimbursement request via application/json body (without file attachment).
     *
     * POST /api/employee/reimbursements
     * Content-Type: application/json
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReimbursementJson(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody EmployeeReimbursementRequestDTO dto) {

        String method = "createReimbursementJson";
        String employeeId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        log.info("[{}] 📥 Incoming JSON reimbursement request | employeeId={} | organizationId={} | type={} | amount={}",
                method, employeeId, organizationId, dto.getReimbursementType(), dto.getRequestedAmount());

        EmployeeReimbursementResponseDTO created = reimbursementService.createReimbursement(
                employeeId, organizationId, dto, List.of());

        log.info("[{}] ✅ Reimbursement created successfully | reimbursementId={}", method, created.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Reimbursement request submitted successfully.");
        response.put("data", created);

        log.info("[{}] 📤 Response sent successfully | reimbursementId={}", method, created.getId());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ====================== GET: Fetch All (Own History) ======================

    /**
     * Fetch all reimbursement requests for the authenticated employee.
     *
     * GET /api/employee/reimbursements
     *
     * @param organizationId from request header
     * @return 200 OK with list of reimbursement DTOs (newest first)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEmployeeReimbursements(
            @RequestHeader("organizationId") String organizationId) {

        String method = "getEmployeeReimbursements";
        String employeeId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        log.info("[{}] 📥 Incoming request to fetch reimbursement history | employeeId={} | organizationId={}",
                method, employeeId, organizationId);

        List<EmployeeReimbursementResponseDTO> reimbursements = reimbursementService.getEmployeeReimbursements(
                employeeId, organizationId);

        log.info("[{}] ✅ Reimbursement history fetched | count={}", method, reimbursements.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursements fetched successfully.");
        response.put("data", reimbursements);

        log.info("[{}] 📤 Response sent successfully", method);

        return ResponseEntity.ok(response);
    }

    // ====================== GET BY ID: Fetch Single Record ======================

    /**
     * Fetch a single reimbursement request by ID for the authenticated employee.
     * Returns 404 if the record does not exist or belongs to a different employee.
     *
     * GET /api/employee/reimbursements/{id}
     *
     * @param id             reimbursement request ID (path variable)
     * @param organizationId from request header
     * @return 200 OK with the reimbursement DTO, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReimbursementDetails(
            @PathVariable Long id,
            @RequestHeader("organizationId") String organizationId) {

        String method = "getReimbursementDetails";
        String employeeId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        log.info("[{}] 📥 Incoming request for reimbursement details | id={} | employeeId={} | organizationId={}",
                method, id, employeeId, organizationId);

        EmployeeReimbursementResponseDTO details = reimbursementService.getReimbursementDetails(
                id, employeeId, organizationId);

        log.info("[{}] ✅ Reimbursement details fetched | reimbursementId={}", method, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement details fetched successfully.");
        response.put("data", details);

        log.info("[{}] 📤 Response sent successfully | reimbursementId={}", method, id);

        return ResponseEntity.ok(response);
    }
}
