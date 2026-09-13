package com.itsdev.payroll.service.employeereimbursement;

import com.itsdev.payroll.dto.employeereimbursement.AdminReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.ApproveReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.RejectReimbursementRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service contract for Employee & Admin Reimbursement modules.
 *
 * Employee methods require employeeId (from JWT) and organizationId (from header).
 * Admin methods require organizationId (from header) and adminId (from JWT) where applicable.
 */
public interface EmployeeReimbursementService {

    // ==================== Employee Operations ====================

    /**
     * Submit a new reimbursement request with optional multipart attachment files.
     * All provided files are uploaded to Cloudinary. URLs, public IDs, and filenames
     * are stored as JSON arrays in the database.
     *
     * @param employeeId     the JWT subject of the authenticated employee
     * @param organizationId the organization ID from the request header
     * @param dto            validated request payload from the client
     * @param files          optional list of attachment files (PDF, JPG, PNG — up to 10 MB each)
     * @return the created reimbursement as a response DTO
     */
    EmployeeReimbursementResponseDTO createReimbursement(
            String employeeId,
            String organizationId,
            EmployeeReimbursementRequestDTO dto,
            List<MultipartFile> files);

    /**
     * Backward-compatible overload without any file.
     */
    EmployeeReimbursementResponseDTO createReimbursement(
            String employeeId,
            String organizationId,
            EmployeeReimbursementRequestDTO dto);

    /**
     * Retrieve all reimbursement requests submitted by the authenticated employee
     * within the given organization, ordered newest-first.
     *
     * @param employeeId     the JWT subject of the authenticated employee
     * @param organizationId the organization ID from the request header
     * @return list of the employee's reimbursement requests
     */
    List<EmployeeReimbursementResponseDTO> getEmployeeReimbursements(
            String employeeId,
            String organizationId);

    /**
     * Retrieve a single reimbursement request by ID for the employee.
     *
     * @param id             the reimbursement request ID
     * @param employeeId     the JWT subject of the authenticated employee
     * @param organizationId the organization ID from the request header
     * @return the reimbursement details DTO
     * @throws com.itsdev.payroll.exception.ResourceNotFoundException if not found
     */
    EmployeeReimbursementResponseDTO getReimbursementDetails(
            Long id,
            String employeeId,
            String organizationId);

    // ==================== Admin Operations ====================

    /**
     * Retrieve all reimbursement requests within the organization for the Admin Portal.
     * Includes employeeName and full details.
     *
     * @param organizationId the organization ID from the request header
     * @return list of all reimbursements in the organization
     */
    List<AdminReimbursementResponseDTO> getAdminReimbursements(String organizationId);

    /**
     * Retrieve complete details of a single reimbursement request by ID for Admin Review.
     *
     * @param id             the reimbursement request ID
     * @param organizationId the organization ID from the request header
     * @return complete Admin reimbursement details DTO
     * @throws com.itsdev.payroll.exception.ResourceNotFoundException if not found
     */
    AdminReimbursementResponseDTO getAdminReimbursementById(Long id, String organizationId);

    /**
     * Approve a reimbursement request by setting the approved amount, remarks, admin audit,
     * and status = APPROVED while keeping paymentStatus = UNPAID.
     *
     * @param id             the reimbursement request ID
     * @param organizationId the organization ID from the request header
     * @param adminId        the authenticated admin ID from JWT
     * @param dto            approval payload with approvedAmount and optional remarks
     * @return updated Admin reimbursement details DTO
     */
    AdminReimbursementResponseDTO approveReimbursement(
            Long id,
            String organizationId,
            String adminId,
            ApproveReimbursementRequestDTO dto);

    /**
     * Reject a reimbursement request with remarks and admin audit.
     * Status becomes REJECTED and paymentStatus remains UNPAID.
     *
     * @param id             the reimbursement request ID
     * @param organizationId the organization ID from the request header
     * @param adminId        the authenticated admin ID from JWT
     * @param dto            rejection payload with remarks
     * @return updated Admin reimbursement details DTO
     */
    AdminReimbursementResponseDTO rejectReimbursement(
            Long id,
            String organizationId,
            String adminId,
            RejectReimbursementRequestDTO dto);
}
