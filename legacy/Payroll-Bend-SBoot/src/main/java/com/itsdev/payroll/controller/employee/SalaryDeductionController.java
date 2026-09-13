package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.SalaryDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.GridDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.SalaryDeductionResponseDTO;
import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.service.employee.SalaryDeductionService;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.util.JWTUtil;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employee-deductions")
public class SalaryDeductionController {

    private final SalaryDeductionService salaryDeductionService;
    private final CloudinaryService cloudinaryService;

    public SalaryDeductionController(
            SalaryDeductionService salaryDeductionService,
            CloudinaryService cloudinaryService
    ) {
        this.salaryDeductionService = salaryDeductionService;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/grid")
    public ResponseEntity<Map<String, Object>> createGridDeduction(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody GridDeductionRequestDTO request
    ) {
        String currentUser = JWTUtil.getCurrentUserName();
        Map<String, Object> result = salaryDeductionService.createGridDeduction(organizationId, request, currentUser);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Grid deduction processed");
        response.put("data", result);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadProof(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam("employeeId") String employeeId,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 400);
            error.put("message", "File is required");
            return ResponseEntity.badRequest().body(error);
        }

        // Validate content type (Images + PDF)
        String contentType = file.getContentType();
        if (contentType == null || !(
                contentType.equalsIgnoreCase("image/jpeg") ||
                contentType.equalsIgnoreCase("image/jpg") ||
                contentType.equalsIgnoreCase("image/png") ||
                contentType.equalsIgnoreCase("image/webp") ||
                contentType.equalsIgnoreCase("application/pdf")
        )) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 400);
            error.put("message", "Only JPG, JPEG, PNG, WEBP, and PDF files are allowed");
            return ResponseEntity.badRequest().body(error);
        }

        // Validate file size (max 5 MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 400);
            error.put("message", "File size must not exceed 5 MB");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // Validate that the target employee belongs to the caller's organization
            salaryDeductionService.validateEmployeeBelongsToOrg(employeeId, organizationId);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 400);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // Multi-tenant path: payroll/deductions/{organizationId}/{employeeId}
            String path = "payroll/deductions/" + organizationId + "/" + employeeId;
            CloudinaryUploadResponseDTO uploadResponse = cloudinaryService.uploadFile(file, path);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("proofUrl", uploadResponse.getSecure_url());
            data.put("proofPublicId", uploadResponse.getPublic_id());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", 200);
            response.put("message", "File uploaded successfully");
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 500);
            error.put("message", "Failed to upload file to Cloudinary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDeductions(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SalaryDeductionResponseDTO> pageResult = salaryDeductionService.getAllDeductions(
                organizationId, search, month, status, pageable
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Deductions retrieved successfully");
        response.put("data", pageResult.getContent());
        response.put("page", pageResult.getNumber());
        response.put("size", pageResult.getSize());
        response.put("totalElements", pageResult.getTotalElements());
        response.put("totalPages", pageResult.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDeduction(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("id") Long id,
            @Valid @RequestBody SalaryDeductionRequestDTO request
    ) {
        String currentUser = JWTUtil.getCurrentUserName();
        SalaryDeductionResponseDTO responseDTO = salaryDeductionService.updateDeduction(organizationId, id, request, currentUser);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Deduction updated successfully");
        response.put("data", responseDTO);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDeduction(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("id") Long id
    ) {
        salaryDeductionService.deleteDeduction(organizationId, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Deduction deleted successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-deductions")
    public ResponseEntity<Map<String, Object>> getMyDeductions(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "month", required = false) String month
    ) {
        String employeeId = JWTUtil.getUserIdAndEmailFromToken().get("userId");
        if (employeeId == null || employeeId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", 401);
            error.put("message", "Unauthorized: employeeId not found in token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        java.util.List<SalaryDeductionResponseDTO> myDeductions = salaryDeductionService.getMyDeductions(organizationId, employeeId, month);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee deductions retrieved successfully");
        response.put("data", myDeductions);

        return ResponseEntity.ok(response);
    }
}
