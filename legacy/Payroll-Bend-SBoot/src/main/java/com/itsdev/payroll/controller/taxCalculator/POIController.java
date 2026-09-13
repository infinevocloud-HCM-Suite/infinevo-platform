package com.itsdev.payroll.controller.taxCalculator;

import com.itsdev.payroll.dto.taxCalculator.POIDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.POISubmissionRequest;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import com.itsdev.payroll.repository.taxCalculator.ProofOfInvestmentDocumentRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.taxCalculator.POIService;
import com.itsdev.payroll.util.JWTUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proof-of-investment")
public class POIController {

    private static final Logger log = LoggerFactory.getLogger(POIController.class);

    private final POIService poiService;
    private final ProofOfInvestmentDocumentRepository poiRepository; // ADD THIS
    private final CloudinaryService cloudinaryService; // ADD THIS

    public POIController(POIService poiService, ProofOfInvestmentDocumentRepository poiRepository,
            CloudinaryService cloudinaryService) {
        this.poiService = poiService;
        this.poiRepository = poiRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // ===== EMPLOYEE ENDPOINTS =====

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam Integer financialYear) {

        Long employeeId = getCurrentEmployeeId();
        log.info("GET /api/proof-of-investment/overview - employee: {}, org: {}, year: {}",
                employeeId, organizationId, financialYear);

        try {
            List<POIDocumentDTO> documents = poiService.getEmployeePOIDocuments(employeeId, organizationId,
                    financialYear);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "POI documents fetched successfully");
            response.put("data", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching POI documents", e);
            return createErrorResponse("Failed to fetch POI documents: " + e.getMessage());
        }
    }

    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestHeader("organizationId") String organizationId,
            @ModelAttribute POISubmissionRequest request) {

        Long employeeId = getCurrentEmployeeId();
        log.info("POST /api/proof-of-investment/documents - employee: {}, org: {}, item: {}, type: {}",
                employeeId, organizationId, request.getDeclaredItemName(), request.getDocumentType());

        try {
            POIDocumentDTO document = poiService.uploadDocument(request, employeeId, organizationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Document uploaded successfully");
            response.put("data", document);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading POI document", e);
            return createErrorResponse("Failed to upload document: " + e.getMessage());
        }
    }

    @PutMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long documentId,
            @ModelAttribute POISubmissionRequest request) {

        log.info("PUT /api/proof-of-investment/documents/{} - org: {}", documentId, organizationId);

        try {
            POIDocumentDTO document = poiService.updateDocument(documentId, request, organizationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Document updated successfully");
            response.put("data", document);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating POI document", e);
            return createErrorResponse("Failed to update document: " + e.getMessage());
        }
    }

    @GetMapping("/documents/{documentId}/view")
    public ResponseEntity<Map<String, Object>> viewDocument(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long documentId) {

        log.info("GET /api/proof-of-investment/documents/{}/view - org: {}", documentId, organizationId);

        try {
            POIDocumentDTO document = poiService.getDocument(documentId, organizationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Document fetched successfully");
            response.put("data", document);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching POI document", e);
            return createErrorResponse("Failed to fetch document: " + e.getMessage());
        }
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long documentId) {

        log.info("DELETE /api/proof-of-investment/documents/{} - org: {}", documentId, organizationId);

        try {
            boolean deleted = poiService.deleteDocument(documentId, organizationId);

            Map<String, Object> response = new LinkedHashMap<>();
            if (deleted) {
                response.put("status", HttpStatus.OK.value());
                response.put("message", "Document deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "Failed to delete document");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            log.error("Error deleting POI document", e);
            return createErrorResponse("Failed to delete document: " + e.getMessage());
        }
    }

    // ===== HR/ADMIN ENDPOINTS =====

    @GetMapping("/admin/overview")
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam Integer financialYear,
            @RequestParam(required = false) ProofOfInvestmentDocument.DocumentStatus status) {

        log.info("GET /api/proof-of-investment/admin/overview - org: {}, year: {}, status: {}",
                organizationId, financialYear, status);

        try {
            List<POIDocumentDTO> documents = poiService.getAllPOIDocuments(organizationId, financialYear, status);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "All POI documents fetched successfully");
            response.put("data", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching all POI documents", e);
            return createErrorResponse("Failed to fetch documents: " + e.getMessage());
        }
    }

    @PutMapping("/admin/documents/{documentId}/status")
    public ResponseEntity<Map<String, Object>> updateDocumentStatus(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long documentId,
            @RequestParam ProofOfInvestmentDocument.DocumentStatus status,
            @RequestParam(required = false) String remarks) {

        String reviewedBy = getCurrentUserId();
        log.info("PUT /api/proof-of-investment/admin/documents/{}/status - org: {}, status: {}, reviewedBy: {}",
                documentId, organizationId, status, reviewedBy);

        try {
            POIDocumentDTO document = poiService.updateDocumentStatus(documentId, status, remarks, reviewedBy,
                    organizationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Document status updated successfully");
            response.put("data", document);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating document status", e);
            return createErrorResponse("Failed to update document status: " + e.getMessage());
        }
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Map<String, Object>> getDownloadUrl(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long documentId) {

        log.info("GET /api/proof-of-investment/documents/{}/download - org: {}", documentId, organizationId);

        try {
            ProofOfInvestmentDocument document = poiRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            if (!document.getOrganizationId().equals(organizationId)) {
                throw new RuntimeException("Document does not belong to this organization");
            }

            // Get download URL from Cloudinary
            String downloadUrl = cloudinaryService.getDownloadUrl(document.getPublicId());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Download URL generated successfully");
            response.put("data", Map.of(
                    "downloadUrl", downloadUrl,
                    "fileName", document.getFileName(),
                    "contentType", document.getContentType()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating download URL", e);
            return createErrorResponse("Failed to generate download URL: " + e.getMessage());
        }
    }

    // ===== PRIVATE HELPER METHODS =====

    private Long getCurrentEmployeeId() {
        try {
            // Get email from JWT
            String email = JWTUtil.getUserIdAndEmailFromToken().get("email");

            // TEMPORARY: Hardcode the mapping until we set up proper database lookup
            if ("lalu123@gmail.com".equals(email)) {
                return 96L; // Replace with actual employee ID
            }

            throw new RuntimeException("No employee mapping found for email: " + email);

        } catch (Exception e) {
            throw new RuntimeException("Unable to get employee ID: " + e.getMessage());
        }
    }

    private String getCurrentUserId() {
        try {
            // Get user ID from JWT
            return JWTUtil.getUserIdAndEmailFromToken().get("userId");
        } catch (Exception e) {
            throw new RuntimeException("Unable to get user ID: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("message", message);
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}