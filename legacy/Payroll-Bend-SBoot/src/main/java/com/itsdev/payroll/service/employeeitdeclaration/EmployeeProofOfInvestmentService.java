package com.itsdev.payroll.service.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeeProofOfInvestmentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeeProofOfInvestmentResponseDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemCommentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIItemDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.AddCommentRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.ConsiderForITResponse;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.UpdateCommentRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIDocumentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemUpdateRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POISubmitRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.List;

public interface EmployeeProofOfInvestmentService {

        // ======================= COMMENT METHODS =======================

        /**
         * Add comment to a POI item
         */
        POIItemCommentDTO addCommentToPoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId,
                        AddCommentRequest request);

        /**
         * Get all comments for a POI item
         */
        List<POIItemCommentDTO> getCommentsForPoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId);

        /**
         * Update a comment
         */
        POIItemCommentDTO updateComment(
                        String organizationId,
                        String employeeId,
                        Long commentId,
                        UpdateCommentRequest request);

        /**
         * Delete a comment
         */
        void deleteComment(
                        String organizationId,
                        String employeeId,
                        Long commentId);

        // ======================= GET / INITIALIZE =======================

        /**
         * Get or initialize POI for an employee for a fiscal year
         * If POI doesn't exist, creates it from IT declaration
         */
        EmployeeProofOfInvestmentDTO getOrInitializePoi(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear);

        // ======================= POI ITEM OPERATIONS =======================

        /**
         * Update a POI item (actual amount, etc.)
         */
        EmployeePOIItemDTO updatePoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId,
                        POIItemUpdateRequest request);

        /**
         * Delete a POI item
         */
        void deletePoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId);

        // ======================= DOCUMENT OPERATIONS =======================

        /**
         * Upload multiple documents for a POI item
         */
        List<EmployeePOIDocumentDTO> uploadDocuments(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId,
                        List<MultipartFile> files) throws IOException;

        /**
         * Delete a document
         */
        void deleteDocument(
                        String organizationId,
                        String employeeId,
                        Long documentId);

        // ======================= WORKFLOW OPERATIONS =======================

        /**
         * Submit POI for review
         */
        EmployeeProofOfInvestmentDTO submitPoi(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        POISubmitRequest request);

        /**
         * Withdraw submitted POI (back to draft)
         */
        EmployeeProofOfInvestmentDTO withdrawPoi(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear);

        // ======================= ADMIN OPERATIONS =======================

        /**
         * Get POI for admin review
         */
        EmployeeProofOfInvestmentDTO getPoiForReview(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear);

        /**
         * Approve a POI item (admin action)
         */
        EmployeePOIItemDTO approvePoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId,
                        String adminComment,
                        java.math.BigDecimal approvedAmount);

        /**
         * Reject a POI item (admin action)
         */
        EmployeePOIItemDTO rejectPoiItem(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        Long poiItemId,
                        String adminComment);

        /**
         * Final approve entire POI (admin action)
         */
        EmployeeProofOfInvestmentDTO finalApprovePoi(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear);

        /**
         * Final reject entire POI (admin action)
         */
        EmployeeProofOfInvestmentDTO finalRejectPoi(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear,
                        String adminComment);

        /**
         * Get all employees' POI submissions for admin dashboard
         */
        Page<EmployeeProofOfInvestmentResponseDTO> getAllPOISubmissions(
                        String organizationId,
                        Integer fiscalYear,
                        String status,
                        String taxRegime,
                        int page,
                        int perPage);

        /**
         * Consider approved POI for IT calculation (admin action)
         * This is a one-time irreversible action
         */
        ConsiderForITResponse considerPOIForIT(
                        String organizationId,
                        String employeeId,
                        Integer fiscalYear);

}