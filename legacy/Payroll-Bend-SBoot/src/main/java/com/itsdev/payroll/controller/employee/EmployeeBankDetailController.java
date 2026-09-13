package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.EmployeeBankDetailDTO;
import com.itsdev.payroll.service.employee.EmployeeBankDetailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees/bank-details")
public class EmployeeBankDetailController {

    private final EmployeeBankDetailService service;
    
    private static final Logger log = LoggerFactory.getLogger(EmployeeBankDetailController.class);
    
    

    public EmployeeBankDetailController(EmployeeBankDetailService service) {
        this.service = service;
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> createBankDetails(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeeBankDetailDTO dto) {

        validatePaymentMode(dto);

        EmployeeBankDetailDTO created = service.create(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Bank details created successfully");
        response.put("data", created);

        return ResponseEntity.status(201).body(response);
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @RequestBody EmployeeBankDetailDTO dto) {

        validatePaymentMode(dto);

        EmployeeBankDetailDTO updated = service.update(organizationId, employeeId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Bank details updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    // ---------------- GET BY EMPLOYEE ID ----------------
    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getByEmployeeId(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        EmployeeBankDetailDTO dto = service.getByEmployeeId(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Bank details retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    // ---------------- LIST ALL ----------------
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestHeader("organizationId") String organizationId) {

        List<EmployeeBankDetailDTO> list = service.getAll(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "All bank details retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    // ---------------- DELETE ----------------
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long employeeId) {

        service.deleteByEmployeeId(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.NO_CONTENT.value());
        response.put("message", "Bank details deleted successfully");

        return ResponseEntity.ok(response);
    }

    // ---------------- VALIDATION METHOD ----------------
    private void validatePaymentMode(EmployeeBankDetailDTO dto) {
        String mode = dto.getPaymentMode() != null ? dto.getPaymentMode().toLowerCase() : "";

        if ("banktransfer".equals(mode)) {
            if (dto.getBankAccountNumber() == null || dto.getIfscCode() == null || dto.getBankName() == null) {
                throw new IllegalArgumentException("Bank account details are required when payment mode is 'banktransfer'");
            }
        } else if ("check".equals(mode) || "cash".equals(mode)) {
            dto.setBankAccountNumber(null);
            dto.setIfscCode(null);
            dto.setBankName(null);
            dto.setAccountHolderName(null);
            dto.setBankAccountType(null);
        } else {
            throw new IllegalArgumentException("Invalid payment mode. Allowed: banktransfer, check, cash");
        }
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> createBulkBankDetails(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<EmployeeBankDetailDTO> dtoList) {

        String method = "createBulkBankDetails";
        log.info("[{}] 📥 Incoming bulk bank detail create request | orgId={} | totalRecords={}",
                method, organizationId, dtoList != null ? dtoList.size() : 0);

        Map<String, Object> response = new LinkedHashMap<>();

        if (dtoList == null || dtoList.isEmpty()) {
            log.warn("[{}] ⚠️ No records received in request body | orgId={}", method, organizationId);
            response.put("status", 400);
            response.put("message", "No records provided");
            return ResponseEntity.badRequest().body(response);
        }

        List<EmployeeBankDetailDTO> createdList = service.createBulk(organizationId, dtoList);

        log.info("[{}] ✅ Successfully created bank details in bulk | orgId={} | successCount={}",
                method, organizationId, createdList.size());

        response.put("status", 201);
        response.put("message", "Bank details created successfully");
        response.put("data", createdList);

        return ResponseEntity.status(201).body(response);
    }


}
