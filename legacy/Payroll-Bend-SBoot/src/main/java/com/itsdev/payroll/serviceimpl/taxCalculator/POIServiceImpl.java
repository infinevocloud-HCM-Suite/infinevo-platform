package com.itsdev.payroll.serviceimpl.taxCalculator;

import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.dto.taxCalculator.POIDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.POISubmissionRequest;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.taxCalculator.ProofOfInvestmentDocumentRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.taxCalculator.POIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class POIServiceImpl implements POIService {

    private static final Logger log = LoggerFactory.getLogger(POIServiceImpl.class);

    private final ProofOfInvestmentDocumentRepository poiRepository;
    private final BasicDetailsRepository employeeRepository;
    private final CloudinaryService cloudinaryService;

    public POIServiceImpl(ProofOfInvestmentDocumentRepository poiRepository,
            BasicDetailsRepository employeeRepository,
            CloudinaryService cloudinaryService) {
        this.poiRepository = poiRepository;
        this.employeeRepository = employeeRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    public List<POIDocumentDTO> getEmployeePOIDocuments(Long employeeId, String organizationId, Integer financialYear) {
        log.info("Fetching POI documents for employee: {}, org: {}, year: {}", employeeId, organizationId,
                financialYear);

        BasicDetails employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));

        // Verify employee belongs to organization
        if (!employee.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        List<ProofOfInvestmentDocument> documents = poiRepository
                .findByEmployeeAndOrganizationIdAndFinancialYear(employee, organizationId, financialYear);

        return documents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public POIDocumentDTO uploadDocument(POISubmissionRequest request, Long employeeId, String organizationId) {
        log.info("Uploading POI document for employee: {}, org: {}, item: {}",
                employeeId, organizationId, request.getDeclaredItemName());

        BasicDetails employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Verify employee belongs to organization
        if (!employee.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }

        try {
            // Upload to Cloudinary
            String companyUserId = getCompanyUserId();
            CloudinaryUploadResponseDTO uploadResponse = cloudinaryService.uploadFile(file, companyUserId);

            // Create and save POI document
            ProofOfInvestmentDocument document = new ProofOfInvestmentDocument(employee, organizationId,
                    request.getFinancialYear());
            document.setDeclaredItemName(request.getDeclaredItemName());
            document.setDocumentType(request.getDocumentType());
            document.setPublicId(uploadResponse.getPublic_id());
            document.setFileUrl(uploadResponse.getSecure_url());
            document.setFileName(file.getOriginalFilename());
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setStatus(ProofOfInvestmentDocument.DocumentStatus.PENDING);

            ProofOfInvestmentDocument savedDocument = poiRepository.save(document);
            log.info("POI document uploaded successfully. Document ID: {}", savedDocument.getId());

            return convertToDTO(savedDocument);
        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary", e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public POIDocumentDTO updateDocument(Long documentId, POISubmissionRequest request, String organizationId) {
        log.info("Updating POI document: {}, org: {}", documentId, organizationId);

        ProofOfInvestmentDocument document = poiRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("POI document not found"));

        if (!document.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Document does not belong to this organization");
        }

        // Only allow updates if in PENDING status
        if (document.getStatus() != ProofOfInvestmentDocument.DocumentStatus.PENDING) {
            throw new RuntimeException("Cannot update document that is not in PENDING status");
        }

        // Update basic information
        document.setDeclaredItemName(request.getDeclaredItemName());
        document.setDocumentType(request.getDocumentType());
        document.setFinancialYear(request.getFinancialYear());
        document.setModifiedDate(LocalDateTime.now());

        // If new file is provided, update it
        if (request.getFile() != null && !request.getFile().isEmpty()) {
            try {
                // Delete old file from Cloudinary
                cloudinaryService.deleteFile(document.getPublicId());

                // Upload new file
                String companyUserId = getCompanyUserId();
                CloudinaryUploadResponseDTO uploadResponse = cloudinaryService.uploadFile(request.getFile(),
                        companyUserId);

                document.setPublicId(uploadResponse.getPublic_id());
                document.setFileUrl(uploadResponse.getSecure_url());
                document.setFileName(request.getFile().getOriginalFilename());
                document.setContentType(request.getFile().getContentType());
                document.setFileSize(request.getFile().getSize());
            } catch (IOException e) {
                log.error("Error updating file in Cloudinary", e);
                throw new RuntimeException("Failed to update file: " + e.getMessage());
            }
        }

        ProofOfInvestmentDocument savedDocument = poiRepository.save(document);
        log.info("POI document updated successfully. Document ID: {}", savedDocument.getId());

        return convertToDTO(savedDocument);
    }

    @Override
    public POIDocumentDTO getDocument(Long documentId, String organizationId) {
        log.info("Fetching POI document: {}, org: {}", documentId, organizationId);

        ProofOfInvestmentDocument document = poiRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("POI document not found"));

        if (!document.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Document does not belong to this organization");
        }

        return convertToDTO(document);
    }

    @Override
    @Transactional
    public boolean deleteDocument(Long documentId, String organizationId) {
        log.info("Deleting POI document: {}, org: {}", documentId, organizationId);

        ProofOfInvestmentDocument document = poiRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("POI document not found"));

        if (!document.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Document does not belong to this organization");
        }

        // Only allow deletion if in PENDING status
        if (document.getStatus() != ProofOfInvestmentDocument.DocumentStatus.PENDING) {
            throw new RuntimeException("Cannot delete document that is not in PENDING status");
        }

        try {
            // Delete from Cloudinary
            boolean cloudinaryDeleted = cloudinaryService.deleteFile(document.getPublicId());

            if (cloudinaryDeleted) {
                poiRepository.delete(document);
                log.info("POI document deleted successfully. Document ID: {}", documentId);
                return true;
            } else {
                log.error("Failed to delete document from Cloudinary. Document ID: {}", documentId);
                return false;
            }
        } catch (IOException e) {
            log.error("Error deleting file from Cloudinary", e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage());
        }
    }

    @Override
    public List<POIDocumentDTO> getAllPOIDocuments(String organizationId, Integer financialYear,
            ProofOfInvestmentDocument.DocumentStatus status) {
        log.info("Fetching all POI documents for org: {}, year: {}, status: {}", organizationId, financialYear, status);

        List<ProofOfInvestmentDocument> documents;
        if (status != null) {
            documents = poiRepository.findByOrganizationIdAndFinancialYearAndStatus(organizationId, financialYear,
                    status);
        } else {
            documents = poiRepository.findByOrganizationIdAndFinancialYear(organizationId, financialYear);
        }

        return documents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public POIDocumentDTO updateDocumentStatus(Long documentId, ProofOfInvestmentDocument.DocumentStatus status,
            String remarks, String reviewedBy, String organizationId) {
        log.info("Updating document status: {}, org: {}, status: {}, reviewedBy: {}",
                documentId, organizationId, status, reviewedBy);

        ProofOfInvestmentDocument document = poiRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("POI document not found"));

        if (!document.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Document does not belong to this organization");
        }

        document.setStatus(status);
        document.setRemarks(remarks);
        document.setReviewedBy(reviewedBy);
        document.setReviewedDate(LocalDateTime.now());
        document.setModifiedDate(LocalDateTime.now());

        ProofOfInvestmentDocument savedDocument = poiRepository.save(document);
        log.info("Document status updated successfully. Document ID: {}, Status: {}", savedDocument.getId(), status);

        return convertToDTO(savedDocument);
    }

    // ===== PRIVATE HELPER METHODS =====

    private POIDocumentDTO convertToDTO(ProofOfInvestmentDocument document) {
        POIDocumentDTO dto = new POIDocumentDTO();
        dto.setId(document.getId());
        dto.setDeclaredItemName(document.getDeclaredItemName());
        dto.setDocumentType(document.getDocumentType());
        dto.setFileName(document.getFileName());
        dto.setFileUrl(document.getFileUrl());
        dto.setStatus(document.getStatus());
        dto.setRemarks(document.getRemarks());
        dto.setSubmittedDate(document.getSubmittedDate());
        dto.setReviewedDate(document.getReviewedDate());
        dto.setReviewedBy(document.getReviewedBy());
        dto.setFinancialYear(document.getFinancialYear());
        return dto;
    }

    private String getCompanyUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Invalid authentication context");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("companyUserId");
    }
}