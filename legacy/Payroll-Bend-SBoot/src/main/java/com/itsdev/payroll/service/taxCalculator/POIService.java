package com.itsdev.payroll.service.taxCalculator;

import com.itsdev.payroll.dto.taxCalculator.POIDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.POISubmissionRequest;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;

import java.util.List;

public interface POIService {

        // Employee methods
        List<POIDocumentDTO> getEmployeePOIDocuments(Long employeeId, String organizationId, Integer financialYear);

        POIDocumentDTO uploadDocument(POISubmissionRequest request, Long employeeId, String organizationId);

        POIDocumentDTO updateDocument(Long documentId, POISubmissionRequest request, String organizationId);

        POIDocumentDTO getDocument(Long documentId, String organizationId);

        boolean deleteDocument(Long documentId, String organizationId);

        // Admin methods
        List<POIDocumentDTO> getAllPOIDocuments(String organizationId, Integer financialYear,
                        ProofOfInvestmentDocument.DocumentStatus status);

        POIDocumentDTO updateDocumentStatus(Long documentId, ProofOfInvestmentDocument.DocumentStatus status,
                        String remarks, String reviewedBy, String organizationId);
}