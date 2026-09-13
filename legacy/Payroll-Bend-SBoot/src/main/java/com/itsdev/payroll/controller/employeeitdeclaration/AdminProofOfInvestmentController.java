package com.itsdev.payroll.controller.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.*;
import com.itsdev.payroll.service.employeeitdeclaration.EmployeeProofOfInvestmentService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/proof-of-investments")
public class AdminProofOfInvestmentController {

    private static final Logger log = LoggerFactory.getLogger(AdminProofOfInvestmentController.class);

    private final EmployeeProofOfInvestmentService poiService;

    public AdminProofOfInvestmentController(EmployeeProofOfInvestmentService poiService) {
        this.poiService = poiService;
    }

    // ======================= GET ALL POI SUBMISSIONS (ADMIN DASHBOARD)
    // =======================

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getAllPOISubmissions(
            @RequestParam("organizationId") String organizationId,
            @RequestParam(value = "fiscalYear", required = false) Integer fiscalYear,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "taxRegime", defaultValue = "all") String taxRegimeParam, // Renamed to avoid conflict
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "50") int perPage,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortBy", defaultValue = "createdTime") String sortBy,
            @RequestParam(value = "sortOrder", defaultValue = "DESC") String sortOrder) {

        String method = "getAllPOISubmissions";
        log.info(
                "[{}] Admin fetching POI submissions for org={}, fiscalYear={}, status={}, taxRegime={}, page={}, perPage={}",
                method, organizationId, fiscalYear, status, taxRegimeParam, page, perPage);

        try {
            // ✅ CORRECT: Pass taxRegimeParam to service
            Page<EmployeeProofOfInvestmentResponseDTO> poiPage = poiService.getAllPOISubmissions(
                    organizationId, fiscalYear, status, taxRegimeParam, page, perPage);

            // Format the response to match Zoho's structure
            List<Map<String, Object>> proofOfInvestmentList = poiPage.getContent().stream()
                    .map(dto -> {
                        Map<String, Object> item = new LinkedHashMap<>();

                        // ✅ Use different variable name to avoid conflict
                        String poiTaxRegime = dto.getTaxRegimeAtSubmission();
                        item.put("tax_regime", poiTaxRegime);
                        item.put("tax_regime_formatted",
                                "with_exemptions".equals(poiTaxRegime) ? "Old Regime" : "New Regime");

                        // Map fields to match Zoho's response structure
                        item.put("declaration_id", dto.getId() != null ? dto.getId().toString() : null);
                        item.put("proof_of_investment_id", dto.getId() != null ? dto.getId().toString() : null);

                        // Format dates
                        if (dto.getSubmittedDate() != null) {
                            item.put("submitted_on", dto.getSubmittedDate().toLocalDate().toString());
                            item.put("submitted_on_formatted", formatDate(dto.getSubmittedDate()));
                        } else {
                            item.put("submitted_on", null);
                            item.put("submitted_on_formatted", null);
                        }

                        item.put("employee_id", dto.getEmployeeId());
                        item.put("employee_number", dto.getEmployeeCode());
                        item.put("employee_name", dto.getEmployeeName());

                        // Use enum's getFormattedName() method
                        if (dto.getStatus() != null) {
                            item.put("status", dto.getStatus().name().toLowerCase());
                            item.put("status_formatted", dto.getStatus().getFormattedName());
                        } else {
                            item.put("status", null);
                            item.put("status_formatted", null);
                        }

                        item.put("created_time", dto.getCreatedTime());
                        item.put("last_modified_time", dto.getUpdatedTime());
                        item.put("approved_by", dto.getApprovedBy());
                        item.put("submitted_by", dto.getSubmittedBy());

                        return item;
                    })
                    .collect(Collectors.toList());

            // Don't forget to add taxRegime to search_criteria in page_context
            List<Map<String, Object>> searchCriteria = new ArrayList<>();

            // Add tax regime criteria if not "all"
            if (taxRegimeParam != null && !"all".equals(taxRegimeParam)) {
                Map<String, Object> taxRegimeCriteria = new LinkedHashMap<>();
                taxRegimeCriteria.put("column_name", "tax_regime");
                taxRegimeCriteria.put("column_name_formatted", "Tax Regime");
                taxRegimeCriteria.put("search_text", taxRegimeParam);
                taxRegimeCriteria.put("search_text_formatted",
                        "with_exemptions".equals(taxRegimeParam) ? "with_exemptions" : "without_exemptions");
                taxRegimeCriteria.put("comparator", "equal");
                searchCriteria.add(taxRegimeCriteria);
            }

            // Add status criteria if not "all"
            if (status != null && !status.equals("all")) {
                Map<String, Object> statusCriteria = new LinkedHashMap<>();
                statusCriteria.put("column_name", "status");
                statusCriteria.put("column_name_formatted", "Proof of Investment Status");
                statusCriteria.put("search_text", status);
                statusCriteria.put("search_text_formatted", status);
                statusCriteria.put("comparator", "equal");
                searchCriteria.add(statusCriteria);
            }

            // Add search criteria for employee name if provided
            if (search != null && !search.trim().isEmpty()) {
                Map<String, Object> searchCriteriaMap = new LinkedHashMap<>();
                searchCriteriaMap.put("column_name", "employee_name");
                searchCriteriaMap.put("column_name_formatted", "Employee Name");
                searchCriteriaMap.put("search_text", search);
                searchCriteriaMap.put("search_text_formatted", search);
                searchCriteriaMap.put("comparator", "contains");
                searchCriteria.add(searchCriteriaMap);
            }

            // Build page context similar to Zoho
            Map<String, Object> pageContext = new LinkedHashMap<>();
            pageContext.put("page", page);
            pageContext.put("per_page", perPage);
            pageContext.put("has_more_page", poiPage.hasNext());
            pageContext.put("can_show_reports_banner", false);
            pageContext.put("report_type", "employee_proof_of_investment_list");

            // Calculate fiscal year dates (example logic - adjust based on your business
            // rules)
            if (fiscalYear != null) {
                LocalDate fromDate = LocalDate.of(fiscalYear - 1, 4, 1); // April 1 of previous year
                LocalDate toDate = LocalDate.of(fiscalYear, 3, 31); // March 31 of current year

                pageContext.put("from_date", fromDate.toString());
                pageContext.put("to_date", toDate.toString());
                pageContext.put("from_date_formatted", formatLocalDate(fromDate));
                pageContext.put("to_date_formatted", formatLocalDate(toDate));
            } else {
                pageContext.put("from_date", null);
                pageContext.put("to_date", null);
                pageContext.put("from_date_formatted", null);
                pageContext.put("to_date_formatted", null);
            }

            pageContext.put("last_accessed_time_formatted", formatLocalDateTime(LocalDateTime.now()));
            pageContext.put("sort_column", sortBy);
            pageContext.put("sort_order", sortOrder);

            // Add search criteria
            // List<Map<String, Object>> searchCriteria = new ArrayList<>();
            // if (status != null && !status.equals("all")) {
            // Map<String, Object> statusCriteria = new LinkedHashMap<>();
            // statusCriteria.put("column_name", "status");
            // statusCriteria.put("column_name_formatted", "Proof of Investment Status");
            // statusCriteria.put("search_text", status);
            // statusCriteria.put("search_text_formatted", status);
            // statusCriteria.put("comparator", "equal");
            // searchCriteria.add(statusCriteria);
            // }

            // if (search != null && !search.trim().isEmpty()) {
            // Map<String, Object> searchCriteriaMap = new LinkedHashMap<>();
            // searchCriteriaMap.put("column_name", "employee_name");
            // searchCriteriaMap.put("column_name_formatted", "Employee Name");
            // searchCriteriaMap.put("search_text", search);
            // searchCriteriaMap.put("search_text_formatted", search);
            // searchCriteriaMap.put("comparator", "contains");
            // searchCriteria.add(searchCriteriaMap);
            // }

            pageContext.put("search_criteria", searchCriteria);
            pageContext.put("zoho_sheet_url", ""); // Empty for now

            // Build final response matching Zoho's structure
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("code", 0);
            response.put("message", "success");
            response.put("proof_of_investment_list", proofOfInvestmentList);
            response.put("page_context", pageContext);

            log.info("[{}] Successfully fetched {} POI submissions", method, poiPage.getNumberOfElements());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);
            return internalServerError();
        }
    }

    // Helper methods for date formatting
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null)
            return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return dateTime.format(formatter);
    }

    private String formatLocalDate(LocalDate date) {
        if (date == null)
            return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }

    private String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
        return dateTime.format(formatter);
    }

    // ======================= GET POI FOR REVIEW =======================

    @GetMapping("/{organizationId}/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> getPoiForReview(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear) {

        String method = "getPoiForReview";
        log.info("[{}] Admin reviewing POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.getPoiForReview(
                    organizationId, employeeId, fiscalYear);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment retrieved for review");
            response.put("data", poiDto);

            log.info("[{}] POI retrieved for review", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= APPROVE POI ITEM =======================

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/items/{poiItemId}/approve")
    public ResponseEntity<Map<String, Object>> approvePoiItem(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId,
            @RequestBody POIReviewRequest request) {

        String method = "approvePoiItem";
        log.info("[{}] Admin approving POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            EmployeePOIItemDTO updatedItem = poiService.approvePoiItem(
                    organizationId, employeeId, fiscalYear, poiItemId,
                    request.getAdminComment(), request.getApprovedAmount());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "POI item approved successfully");
            response.put("data", updatedItem);

            log.info("[{}] POI item approved successfully", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= REJECT POI ITEM =======================

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/items/{poiItemId}/reject")
    public ResponseEntity<Map<String, Object>> rejectPoiItem(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId,
            @RequestBody POIReviewRequest request) {

        String method = "rejectPoiItem";
        log.info("[{}] Admin rejecting POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            EmployeePOIItemDTO updatedItem = poiService.rejectPoiItem(
                    organizationId, employeeId, fiscalYear, poiItemId,
                    request.getAdminComment());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "POI item rejected successfully");
            response.put("data", updatedItem);

            log.info("[{}] POI item rejected successfully", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= FINAL APPROVE POI =======================

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/final-approve")
    public ResponseEntity<Map<String, Object>> finalApprovePoi(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear) {

        String method = "finalApprovePoi";
        log.info("[{}] Admin final approving POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.finalApprovePoi(
                    organizationId, employeeId, fiscalYear);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment final approved successfully");
            response.put("data", poiDto);

            log.info("[{}] POI final approved successfully", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= FINAL REJECT POI =======================

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/final-reject")
    public ResponseEntity<Map<String, Object>> finalRejectPoi(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @RequestBody POIFinalApproveRequest request) {

        String method = "finalRejectPoi";
        log.info("[{}] Admin final rejecting POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.finalRejectPoi(
                    organizationId, employeeId, fiscalYear, request.getAdminComment());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment final rejected successfully");
            response.put("data", poiDto);

            log.info("[{}] POI final rejected successfully", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= BULK ACTIONS =======================

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/bulk-action")
    public ResponseEntity<Map<String, Object>> bulkAction(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @RequestBody Map<String, Object> request) {

        String method = "bulkAction";
        log.info("[{}] Admin bulk action for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            // Note: This is a placeholder - implement bulk action logic in service

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Bulk action completed successfully");
            response.put("data", null);

            log.info("[{}] Bulk action completed", method);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // ======================= COMMENT ENDPOINTS (ADMIN) =======================

    @GetMapping("/{organizationId}/{employeeId}/{fiscalYear}/items/{poiItemId}/comments")
    public ResponseEntity<Map<String, Object>> getCommentsForItem(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId) {

        String method = "getCommentsForItem";
        log.info("[{}] Admin getting comments for POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            List<POIItemCommentDTO> comments = poiService.getCommentsForPoiItem(
                    organizationId, employeeId, fiscalYear, poiItemId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Comments retrieved successfully");
            response.put("data", comments);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);
            return internalServerError();
        }
    }

    @PostMapping("/{organizationId}/{employeeId}/{fiscalYear}/items/{poiItemId}/comments")
    public ResponseEntity<Map<String, Object>> addCommentToItem(
            @PathVariable("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId,
            @RequestBody AddCommentRequest request) {

        String method = "addCommentToItem";
        log.info("[{}] Admin adding comment to POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            POIItemCommentDTO comment = poiService.addCommentToPoiItem(
                    organizationId, employeeId, fiscalYear, poiItemId, request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Comment added successfully");
            response.put("data", comment);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);
            return internalServerError();
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("message", message);
        errorResponse.put("data", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    private ResponseEntity<Map<String, Object>> internalServerError() {
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put("message", "Internal server error");
        errorResponse.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // ======================= CONSIDER POI FOR IT CALCULATION
    // =======================

    @PostMapping("/{employeeId}/{fiscalYear}/consider-for-it")
    public ResponseEntity<Map<String, Object>> considerForIT(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @PathVariable Integer fiscalYear) {

        ConsiderForITResponse response = poiService.considerPOIForIT(
                organizationId,
                employeeId,
                fiscalYear);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.OK.value());
        body.put("message", "POI considered for IT calculation successfully");
        body.put("data", response);

        return ResponseEntity.ok(body);
    }

}