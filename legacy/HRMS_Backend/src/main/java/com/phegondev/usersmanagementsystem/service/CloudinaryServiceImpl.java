package com.phegondev.usersmanagementsystem.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.phegondev.usersmanagementsystem.dto.CloudinaryUploadResponseDto;

@Service
public class CloudinaryServiceImpl  {

    @Autowired
    private Cloudinary cloudinary;

    public CloudinaryUploadResponseDto uploadFile(MultipartFile file, String empId) throws IOException {
    // Create folder path with year/month/day/empId structure
    String folderPath = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + 
            "/" + empId;

    Map<String, Object> uploadOptions = ObjectUtils.asMap(
            "folder", folderPath,
            "public_id", "doc_" + System.currentTimeMillis() + "_" + empId);

    System.out.println("Uploading to folder: " + folderPath);
    System.out.println("Public ID will be: " + uploadOptions.get("public_id"));
    System.out.println("=== Starting file upload to Cloudinary ===");
    System.out.println("Original filename: " + file.getOriginalFilename());
    System.out.println("File size (bytes): " + file.getSize());
    System.out.println("File content type: " + file.getContentType());

    try {
        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

        System.out.println("=== Cloudinary Upload Result ===");
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        CloudinaryUploadResponseDto response = new CloudinaryUploadResponseDto();
        response.setAsset_id((String) result.get("asset_id"));
        response.setPublic_id((String) result.get("public_id"));
        response.setSecure_url((String) result.get("secure_url"));
        response.setFormat((String) result.get("format"));
        response.setWidth((Integer) result.get("width"));
        response.setHeight((Integer) result.get("height"));
        response.setBytes(((Number) result.get("bytes")).longValue());
        response.setCreated_at((String) result.get("created_at"));

        System.out.println("=== Mapped DTO ===");
        System.out.println("Asset ID: " + response.getAsset_id());
        System.out.println("Public ID: " + response.getPublic_id());
        System.out.println("Secure URL: " + response.getSecure_url());
        System.out.println("Format: " + response.getFormat());
        System.out.println("Dimensions: " + response.getWidth() + "x" + response.getHeight());
        System.out.println("Size (bytes): " + response.getBytes());
        System.out.println("Created at: " + response.getCreated_at());

        System.out.println("=== Upload to Cloudinary completed successfully ===");
        return response;
    } catch (Exception e) {
        System.out.println("=== Error uploading to Cloudinary ===");
        e.printStackTrace();
        throw e;
    }
}

    public void deleteFile(String publicId) throws Exception {
    try {
        Map<String, String> options = new HashMap<>();
        options.put("invalidate", "true"); // Optional: invalidate CDN cache
        cloudinary.uploader().destroy(publicId, options);
    } catch (Exception e) {
        System.out.println("Error deleting file from Cloudinary: " + e.getMessage());
        throw e;
    }
}


}
