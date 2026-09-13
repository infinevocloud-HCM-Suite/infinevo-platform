package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.EmployeePersonalDetailDTO;
import com.itsdev.payroll.service.employee.EmployeePersonalDetailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//@RestController
//@RequestMapping("/api/v1/employees/personal-details")
//public class EmployeePersonalDetailController {
//
//    private final EmployeePersonalDetailService service;
//
//    public EmployeePersonalDetailController(EmployeePersonalDetailService service) {
//        this.service = service;
//    }
//
//    // ---------------- CREATE ----------------
//    @PostMapping
//    public ResponseEntity<EmployeePersonalDetailDTO> create(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestBody EmployeePersonalDetailDTO dto) {
//        EmployeePersonalDetailDTO created = service.create(organizationId, dto);
//        return ResponseEntity.ok(created);
//    }
//
//    // ---------------- UPDATE ----------------
//    @PutMapping("/{employeeId}")
//    public ResponseEntity<EmployeePersonalDetailDTO> update(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable Long employeeId,
//            @RequestBody EmployeePersonalDetailDTO dto) {
//        EmployeePersonalDetailDTO updated = service.update(organizationId, employeeId, dto);
//        return ResponseEntity.ok(updated);
//    }
//
//    // ---------------- GET BY EMPLOYEE ID ----------------
//    @GetMapping("/{employeeId}")
//    public ResponseEntity<EmployeePersonalDetailDTO> getByEmployeeId(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable Long employeeId) {
//        EmployeePersonalDetailDTO dto = service.getByEmployeeId(organizationId, employeeId);
//        return ResponseEntity.ok(dto);
//    }
//
//    // ---------------- LIST ALL ----------------
//    @GetMapping
//    public ResponseEntity<List<EmployeePersonalDetailDTO>> getAll(
//            @RequestHeader("organizationId") String organizationId) {
//        List<EmployeePersonalDetailDTO> list = service.getAll(organizationId);
//        return ResponseEntity.ok(list);
//    }
//
//    // ---------------- DELETE BY EMPLOYEE ID ----------------
//    @DeleteMapping("/{employeeId}")
//    public ResponseEntity<Void> deleteByEmployeeId(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable Long employeeId) {
//        service.deleteByEmployeeId(organizationId, employeeId);
//        return ResponseEntity.noContent().build();
//    }
//}

@RestController
@RequestMapping("/api/employees/personal-details")
public class EmployeePersonalDetailController {

    private final EmployeePersonalDetailService service;
    
    private static final Logger log = LoggerFactory.getLogger(EmployeePersonalDetailController.class);

    public EmployeePersonalDetailController(EmployeePersonalDetailService service) {
        this.service = service;
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeePersonalDetailDTO dto) {

        EmployeePersonalDetailDTO created = service.create(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Employee personal detail created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @RequestBody EmployeePersonalDetailDTO dto) {

        EmployeePersonalDetailDTO updated = service.update(organizationId, employeeId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee personal detail updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    // ---------------- GET BY EMPLOYEE ID ----------------
    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getByEmployeeId(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        EmployeePersonalDetailDTO dto = service.getByEmployeeId(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee personal detail retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    // ---------------- LIST ALL ----------------
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestHeader("organizationId") String organizationId) {

        List<EmployeePersonalDetailDTO> list = service.getAll(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee personal details retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    // ---------------- DELETE BY EMPLOYEE ID ----------------
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> deleteByEmployeeId(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long employeeId) {

        service.deleteByEmployeeId(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee personal detail deleted successfully");

        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> createBulk(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<EmployeePersonalDetailDTO> dtoList) {

        String method = "createBulk";
        log.info("[{}] 📥 Incoming bulk employee personal details create request | orgId={} | totalRecords={}",
                method, organizationId, dtoList != null ? dtoList.size() : 0);

        Map<String, Object> response = new LinkedHashMap<>();

        if (dtoList == null || dtoList.isEmpty()) {
            log.warn("[{}] ⚠️ No employee personal records received | orgId={}", method, organizationId);
            response.put("status", 400);
            response.put("message", "No records provided");
            return ResponseEntity.badRequest().body(response);
        }

        List<EmployeePersonalDetailDTO> createdList = service.createBulk(organizationId, dtoList);

        log.info("[{}] ✅ Successfully created employee personal details in bulk | orgId={} | successCount={}",
                method, organizationId, createdList.size());

        response.put("status", 201);
        response.put("message", "Employee personal details created successfully");
        response.put("data", createdList);

        return ResponseEntity.status(201).body(response);
    }


}

