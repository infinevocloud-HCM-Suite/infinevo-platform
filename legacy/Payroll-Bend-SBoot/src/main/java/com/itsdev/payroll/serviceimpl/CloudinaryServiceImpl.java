package com.itsdev.payroll.serviceimpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryServiceImpl.class);

    @Autowired
    private Cloudinary cloudinary;

    private String getCompanyUserIdFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Invalid authentication context");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("companyUserId");
    }

    @Override
    public CloudinaryUploadResponseDTO uploadFile(MultipartFile file, String companyUserId) throws IOException {

        String methodName = "uploadFile"; // for consistent logging

        // Folder path: companyUserId/YYYY/MM/DD
        String folderPath = companyUserId + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        Map<String, Object> uploadOptions = ObjectUtils.asMap(
                "folder", folderPath,
                "public_id", "doc_" + System.currentTimeMillis() + "_" + companyUserId);

        log.info("[{}] Uploading to folder: {}", methodName, folderPath);
        log.info("[{}] Public ID will be: {}", methodName, uploadOptions.get("public_id"));
        log.info("[{}] === Starting file upload to Cloudinary ===", methodName);
        log.info("[{}] Original filename: {}", methodName, file.getOriginalFilename());
        log.info("[{}] File size (bytes): {}", methodName, file.getSize());
        log.info("[{}] File content type: {}", methodName, file.getContentType());

        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            log.info("[{}] Cloudinary Upload Result ", methodName);
            result.forEach((key, value) -> log.info("[{}] {}: {}", methodName, key, value));

            CloudinaryUploadResponseDTO response = new CloudinaryUploadResponseDTO();
            response.setAsset_id((String) result.get("asset_id"));
            response.setPublic_id((String) result.get("public_id"));
            response.setSecure_url((String) result.get("secure_url"));
            response.setFormat((String) result.get("format"));
            response.setWidth((Integer) result.get("width"));
            response.setHeight((Integer) result.get("height"));
            response.setBytes(((Number) result.get("bytes")).longValue());
            response.setCreated_at((String) result.get("created_at"));

            log.info("[{}]  Upload to Cloudinary completed successfully ", methodName);
            return response;
        } catch (Exception e) {
            log.error("[{}] Error uploading to Cloudinary ", methodName, e);
            throw e;
        }
    }
    
    @Override
    public CloudinaryUploadResponseDTO uploadEmployeeInvestmentFile(MultipartFile file,
                                                  String organizationId,
                                                  String employeeId,
                                                  Integer financialYear
                                                 ) throws IOException {

        String methodName = "uploadEmployeeInvestmentFile";

        // Folder Path:
        String folderPath = organizationId + "/"
                + employeeId + "/"
                + financialYear;

        Map<String, Object> uploadOptions = ObjectUtils.asMap(
                "folder", folderPath,
                "public_id", "doc_" + System.currentTimeMillis(),
                "resource_type", "auto"
        );

        log.info("[{}] Uploading to folder: {}", methodName, folderPath);
        log.info("[{}] Public ID will be: {}", methodName, uploadOptions.get("public_id"));
        log.info("[{}] === Starting file upload to Cloudinary ===", methodName);
        log.info("[{}] Original filename: {}", methodName, file.getOriginalFilename());
        log.info("[{}] File size (bytes): {}", methodName, file.getSize());
        log.info("[{}] File content type: {}", methodName, file.getContentType());

        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            log.info("[{}] Cloudinary Upload Result ", methodName);
            result.forEach((key, value) -> log.info("[{}] {}: {}", methodName, key, value));

            CloudinaryUploadResponseDTO response = new CloudinaryUploadResponseDTO();
            response.setAsset_id((String) result.get("asset_id"));
            response.setPublic_id((String) result.get("public_id"));
            response.setSecure_url((String) result.get("secure_url"));
            response.setFormat((String) result.get("format"));
            response.setWidth((Integer) result.get("width"));
            response.setHeight((Integer) result.get("height"));
            response.setBytes(((Number) result.get("bytes")).longValue());
            response.setCreated_at((String) result.get("created_at"));

            log.info("[{}]  Upload to Cloudinary completed successfully ", methodName);
            return response;
        } catch (Exception e) {
            log.error("[{}] Error uploading to Cloudinary ", methodName, e);
            throw e;
        }
    }

    @Override
    public CloudinaryUploadResponseDTO uploadReimbursementAttachment(
            MultipartFile file,
            String organizationId,
            String employeeId) throws IOException {

        String methodName = "uploadReimbursementAttachment";

        // Folder Path: {organizationId}/reimbursements/{employeeId}
        String folderPath = organizationId + "/reimbursements/" + employeeId;

        Map<String, Object> uploadOptions = ObjectUtils.asMap(
                "folder", folderPath,
                "public_id", "reimb_" + System.currentTimeMillis(),
                "resource_type", "auto"
        );

        log.info("[{}] Uploading reimbursement attachment to folder: {}", methodName, folderPath);
        log.info("[{}] Public ID will be: {}", methodName, uploadOptions.get("public_id"));
        log.info("[{}] === Starting file upload to Cloudinary ===", methodName);
        log.info("[{}] Original filename: {}", methodName, file.getOriginalFilename());
        log.info("[{}] File size (bytes): {}", methodName, file.getSize());
        log.info("[{}] File content type: {}", methodName, file.getContentType());

        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            log.info("[{}] Cloudinary Upload Result", methodName);
            result.forEach((key, value) -> log.info("[{}] {}: {}", methodName, key, value));

            CloudinaryUploadResponseDTO response = new CloudinaryUploadResponseDTO();
            response.setAsset_id((String) result.get("asset_id"));
            response.setPublic_id((String) result.get("public_id"));
            response.setSecure_url((String) result.get("secure_url"));
            response.setFormat((String) result.get("format"));
            if (result.get("width") != null) response.setWidth((Integer) result.get("width"));
            if (result.get("height") != null) response.setHeight((Integer) result.get("height"));
            if (result.get("bytes") != null) response.setBytes(((Number) result.get("bytes")).longValue());
            response.setCreated_at((String) result.get("created_at"));

            log.info("[{}] ☁️ Upload to Cloudinary completed successfully | secure_url={}", methodName, response.getSecure_url());
            return response;
        } catch (Exception e) {
            log.error("[{}] ❌ Error uploading reimbursement attachment to Cloudinary", methodName, e);
            throw e;
        }
    }

    @Override
    public boolean deleteFile(String publicId) throws IOException {
        String methodName = "deleteFile";
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String resultStatus = (String) result.get("result");

            log.info("[{}] Cloudinary delete result: {}", methodName, resultStatus);
            return "ok".equalsIgnoreCase(resultStatus);
        } catch (Exception e) {
            log.error("[{}] Error deleting file from Cloudinary", methodName, e);
            throw e;
        }
    }

    @Override
    public String getDownloadUrl(String publicId) {
        try {
            String url = cloudinary.url()
                    .resourceType("auto")
                    .publicId(publicId)
                    .generate();
            log.info("Generated download URL for publicId {}: {}", publicId, url);
            return url;
        } catch (Exception e) {
            log.error("Error generating download URL for publicId: {}", publicId, e);
            throw new RuntimeException("Failed to generate download URL");
        }
    }

}
