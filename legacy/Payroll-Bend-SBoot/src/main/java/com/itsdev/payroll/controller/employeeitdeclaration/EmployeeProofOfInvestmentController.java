package com.itsdev.payroll.controller.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeeProofOfInvestmentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemCommentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIItemDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.AddCommentRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIDocumentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemUpdateRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POISettingsDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POISubmitRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.UpdateCommentRequest;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.service.employeeitdeclaration.EmployeeProofOfInvestmentService;
import com.itsdev.payroll.service.claimsanddeclarations.POISettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/proof-of-investments")
public class EmployeeProofOfInvestmentController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProofOfInvestmentController.class);

    private final EmployeeProofOfInvestmentService poiService;
    private final POISettingsService poiSettingsService;

    public EmployeeProofOfInvestmentController(EmployeeProofOfInvestmentService poiService,
            POISettingsService poiSettingsService) {
        this.poiService = poiService;
        this.poiSettingsService = poiSettingsService;
    }

    // ======================= GET / INITIALIZE POI =======================

    @GetMapping("/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> getOrInitializePoi(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear) {

        String method = "getOrInitializePoi";
        log.info("[{}] Getting/initializing POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.getOrInitializePoi(
                    organizationId, employeeId, fiscalYear);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment retrieved successfully");
            response.put("data", poiDto);

            log.info("[{}] POI retrieved/initialized successfully", method);
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

    // ======================= UPDATE POI ITEM =======================

    @PutMapping("/{employeeId}/{fiscalYear}/items/{poiItemId}")
    public ResponseEntity<Map<String, Object>> updatePoiItem(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId,
            @RequestBody POIItemUpdateRequest request) {

        String method = "updatePoiItem";
        log.info("[{}] Updating POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            EmployeePOIItemDTO updatedItem = poiService.updatePoiItem(
                    organizationId, employeeId, fiscalYear, poiItemId, request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "POI item updated successfully");
            response.put("data", updatedItem);

            log.info("[{}] POI item updated successfully", method);
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

    // ======================= DELETE POI ITEM =======================

    @DeleteMapping("/{employeeId}/{fiscalYear}/items/{poiItemId}")
    public ResponseEntity<Map<String, Object>> deletePoiItem(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId) {

        String method = "deletePoiItem";
        log.info("[{}] Deleting POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        try {
            poiService.deletePoiItem(organizationId, employeeId, fiscalYear, poiItemId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "POI item deleted successfully");
            response.put("data", null);

            log.info("[{}] POI item deleted successfully", method);
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

    // ======================= UPLOAD DOCUMENT =======================

    // @PostMapping(value =
    // "/{employeeId}/{fiscalYear}/items/{poiItemId}/documents",
    // consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // public ResponseEntity<Map<String, Object>> uploadDocument(
    // @RequestHeader("organizationId") String organizationId,
    // @PathVariable("employeeId") String employeeId,
    // @PathVariable("fiscalYear") Integer fiscalYear,
    // @PathVariable("poiItemId") Long poiItemId,
    // @RequestParam("file") MultipartFile file) {

    // String method = "uploadDocument";
    // log.info("[{}] Uploading document for POI item {} for org={}, employee={},
    // fiscalYear={}",
    // method, poiItemId, organizationId, employeeId, fiscalYear);

    // try {
    // // Validate file
    // if (file.isEmpty()) {
    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
    // errorResponse.put("message", "File cannot be empty");
    // errorResponse.put("data", null);
    // return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    // }

    // // Validate file size (max 10MB)
    // if (file.getSize() > 10 * 1024 * 1024) {
    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
    // errorResponse.put("message", "File size exceeds 10MB limit");
    // errorResponse.put("data", null);
    // return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    // }

    // // Validate file type
    // String contentType = file.getContentType();
    // if (contentType == null ||
    // (!contentType.startsWith("image/") &&
    // !contentType.equals("application/pdf") &&
    // !contentType.equals("application/msword") &&
    // !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
    // {
    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
    // errorResponse.put("message", "Only images, PDF, and Word documents are
    // allowed");
    // errorResponse.put("data", null);
    // return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    // }

    // EmployeePOIDocumentDTO documentDto = poiService.uploadDocument(
    // organizationId, employeeId, fiscalYear, poiItemId, file);

    // Map<String, Object> response = new LinkedHashMap<>();
    // response.put("status", HttpStatus.OK.value());
    // response.put("message", "Document uploaded successfully");
    // response.put("data", documentDto);

    // log.info("[{}] Document uploaded successfully: {}", method,
    // documentDto.getDocumentUrl());
    // return ResponseEntity.ok(response);

    // } catch (IOException e) {
    // log.error("[{}] File upload error: {}", method, e.getMessage(), e);

    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    // errorResponse.put("message", "File upload failed: " + e.getMessage());
    // errorResponse.put("data", null);

    // return
    // ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    // } catch (RuntimeException e) {
    // log.error("[{}] Error: {}", method, e.getMessage(), e);

    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
    // errorResponse.put("message", e.getMessage());
    // errorResponse.put("data", null);

    // return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    // } catch (Exception e) {
    // log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

    // Map<String, Object> errorResponse = new LinkedHashMap<>();
    // errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    // errorResponse.put("message", "Internal server error");
    // errorResponse.put("data", null);

    // return
    // ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    // }
    // }

    @PostMapping(value = "/{employeeId}/{fiscalYear}/items/{poiItemId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocuments(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @PathVariable Integer fiscalYear,
            @PathVariable Long poiItemId,
            @RequestParam("files") List<MultipartFile> files) {

        String method = "uploadDocuments";
        log.info("[{}] Uploading {} documents", method, files.size());

        if (files == null || files.isEmpty()) {
            return badRequest("No files provided");
        }

        // validate each file
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                return badRequest("File cannot be empty");
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return badRequest("File size exceeds 10MB limit");
            }
            String contentType = file.getContentType();
            if (contentType == null ||
                    (!contentType.startsWith("image/")
                            && !contentType.equals("application/pdf")
                            && !contentType.equals("application/msword")
                            && !contentType.equals(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
                return badRequest("Invalid file type");
            }
        }

        try {
            List<EmployeePOIDocumentDTO> documents = poiService.uploadDocuments(organizationId, employeeId, fiscalYear,
                    poiItemId, files);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Documents uploaded successfully");
            response.put("data", documents);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("[{}] File upload error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "File upload failed: " + e.getMessage());
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);

            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("message", message);
        error.put("data", null);
        return ResponseEntity.badRequest().body(error);
    }

    private ResponseEntity<Map<String, Object>> internalServerError() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("message", "Internal server error");
        error.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ======================= DELETE DOCUMENT =======================

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @RequestHeader("organizationId") String organizationId,
            @RequestHeader("employeeId") String employeeId,
            @PathVariable("documentId") Long documentId) {

        String method = "deleteDocument";
        log.info("[{}] Deleting document {} for org={}, employee={}", method, documentId, organizationId, employeeId);

        try {
            poiService.deleteDocument(organizationId, employeeId, documentId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Document deleted successfully");
            response.put("data", null);

            log.info("[{}] Document deleted successfully", method);
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

    // ======================= SUBMIT POI =======================

    @PostMapping("/{employeeId}/{fiscalYear}/submit")
    public ResponseEntity<Map<String, Object>> submitPoi(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @RequestBody POISubmitRequest request) {

        String method = "submitPoi";
        log.info("[{}] Submitting POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.submitPoi(
                    organizationId, employeeId, fiscalYear, request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment submitted for review");
            response.put("data", poiDto);

            log.info("[{}] POI submitted successfully", method);
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

    // ======================= WITHDRAW POI =======================

    @PostMapping("/{employeeId}/{fiscalYear}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawPoi(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear) {

        String method = "withdrawPoi";
        log.info("[{}] Withdrawing POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        try {
            EmployeeProofOfInvestmentDTO poiDto = poiService.withdrawPoi(
                    organizationId, employeeId, fiscalYear);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Proof of Investment withdrawn successfully");
            response.put("data", poiDto);

            log.info("[{}] POI withdrawn successfully", method);
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

    // ======================= GET DOCUMENT DOWNLOAD URL =======================

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Map<String, Object>> getDocumentDownloadUrl(
            @PathVariable("documentId") Long documentId) {

        String method = "getDocumentDownloadUrl";
        log.info("[{}] Getting download URL for document {}", method, documentId);

        try {
            // Note: This is a simplified placeholder
            // You need to implement actual logic to get Cloudinary URL

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Download URL retrieved");
            response.put("data", Map.of(
                    "downloadUrl", "/api/proof-of-investments/documents/" + documentId + "/file",
                    "filename", "document_" + documentId));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "Internal server error");
            errorResponse.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ======================= COMMENT ENDPOINTS (EMPLOYEE) =======================

    @GetMapping("/{employeeId}/{fiscalYear}/items/{poiItemId}/comments")
    public ResponseEntity<Map<String, Object>> getCommentsForMyItem(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId) {

        String method = "getCommentsForMyItem";
        log.info("[{}] Employee getting comments for POI item {} for org={}, employee={}, fiscalYear={}",
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

    @PostMapping("/{employeeId}/{fiscalYear}/items/{poiItemId}/comments")
    public ResponseEntity<Map<String, Object>> addCommentToMyItem(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @PathVariable("poiItemId") Long poiItemId,
            @RequestBody AddCommentRequest request) {

        String method = "addCommentToMyItem";
        log.info("[{}] Employee adding comment to POI item {} for org={}, employee={}, fiscalYear={}",
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

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> updateMyComment(
            @RequestHeader("organizationId") String organizationId,
            @RequestHeader("employeeId") String employeeId,
            @PathVariable("commentId") Long commentId,
            @RequestBody UpdateCommentRequest request) {

        String method = "updateMyComment";
        log.info("[{}] Employee updating comment {} for org={}, employee={}",
                method, commentId, organizationId, employeeId);

        try {
            POIItemCommentDTO comment = poiService.updateComment(
                    organizationId, employeeId, commentId, request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Comment updated successfully");
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

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteMyComment(
            @RequestHeader("organizationId") String organizationId,
            @RequestHeader("employeeId") String employeeId,
            @PathVariable("commentId") Long commentId) {

        String method = "deleteMyComment";
        log.info("[{}] Employee deleting comment {} for org={}, employee={}",
                method, commentId, organizationId, employeeId);

        try {
            poiService.deleteComment(organizationId, employeeId, commentId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Comment deleted successfully");
            response.put("data", null);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("[{}] Error: {}", method, e.getMessage(), e);
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", method, e.getMessage(), e);
            return internalServerError();
        }
    }

    // In EmployeeProofOfInvestmentController.java
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getPOISettings(
            @RequestHeader("organizationId") String organizationId) {

        // ✅ CORRECT: Get settings from service ONLY
        POISettingsDTO settingsDto = poiSettingsService.getSettingsDTO(organizationId);

        // ✅ NOTHING ELSE NEEDED! The DTO already has all correct values

        // ✅ Match Zoho's response structure
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0); // Zoho uses code: 0 for success
        response.put("message", "success");
        response.put("data", settingsDto);

        return ResponseEntity.ok(response);
    }
}