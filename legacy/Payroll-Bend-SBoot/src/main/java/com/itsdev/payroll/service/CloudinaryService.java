package com.itsdev.payroll.service;

import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CloudinaryService {

    CloudinaryUploadResponseDTO uploadFile(MultipartFile file, String companyUserId) throws IOException;

    boolean deleteFile(String publicId) throws IOException;

    String getDownloadUrl(String publicId);
    
    CloudinaryUploadResponseDTO uploadEmployeeInvestmentFile(MultipartFile file,
            String organizationId,
            String employeeId,
            Integer financialYear
           ) throws IOException;

    /**
     * Upload an employee reimbursement attachment (bills, receipts, invoices in PDF or image format).
     *
     * @param file           the uploaded file (PDF, JPG, JPEG, PNG)
     * @param organizationId the organization ID for multi-tenant isolation
     * @param employeeId     the authenticated employee ID
     * @return Cloudinary upload response containing secure_url, public_id, and metadata
     * @throws IOException if network or file read fails
     */
    CloudinaryUploadResponseDTO uploadReimbursementAttachment(
            MultipartFile file,
            String organizationId,
            String employeeId
    ) throws IOException;

}
