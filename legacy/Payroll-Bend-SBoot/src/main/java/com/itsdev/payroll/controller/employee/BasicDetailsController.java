//package com.itsdev.payroll.controller.employee;
//
//
//// BasicDetailsController.java
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
//import com.itsdev.payroll.service.employee.BasicDetailsService;
//import jakarta.validation.Valid;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
////
//
//@RestController
//@RequestMapping("/api/employees")
//public class BasicDetailsController {
//
//    private final BasicDetailsService basicDetailsService;
//
//    public BasicDetailsController(BasicDetailsService basicDetailsService) {
//        this.basicDetailsService = basicDetailsService;
//    }
//
//    @PostMapping
//    public ResponseEntity<Map<String, Object>> createBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @Valid @RequestBody BasicDetailsDTO dto) {
//
//        BasicDetailsDTO created = basicDetailsService.createBasicDetails(organizationId, dto);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 201);
//        response.put("message", "Employee created successfully");
//        response.put("data", created);
//
//        return ResponseEntity.ok(response);
//    }
//
//    @PutMapping("/{employeeId}")
//    public ResponseEntity<Map<String, Object>> updateBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable String employeeId,
//            @RequestBody BasicDetailsDTO dto) {
//
//        BasicDetailsDTO updated = basicDetailsService.updateBasicDetails(organizationId, employeeId, dto);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 200);
//        response.put("message", "Employee updated successfully");
//        response.put("data", updated);
//
//        return ResponseEntity.ok(response);
//    }
//
//    @GetMapping("/{employeeId}")
//    public ResponseEntity<Map<String, Object>> getBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable String employeeId) {
//
//        BasicDetailsDTO dto = basicDetailsService.getBasicDetails(organizationId, employeeId);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 200);
//        response.put("message", "Employee retrieved successfully");
//        response.put("data", dto);
//
//        return ResponseEntity.ok(response);
//    }
//
//    @GetMapping
//    public ResponseEntity<Map<String, Object>> getAllBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestParam(value = "page", defaultValue = "0") int page,
//            @RequestParam(value = "size", defaultValue = "20") int size,
//            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
//            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {
//
//        int maxSize = 100;
//        int pageIndex = Math.max(0, page);
//        int pageSize = Math.max(1, Math.min(size, maxSize));
//
//        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
//        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
//
//        Page<BasicDetailsDTO> result = basicDetailsService.getAllBasicDetails(organizationId, pageable);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 200);
//        response.put("message", "Employees retrieved successfully");
//        response.put("data", result);
//
//        return ResponseEntity.ok(response);
//    }
//
//    @DeleteMapping("/{employeeId}")
//    public ResponseEntity<Map<String, Object>> deleteBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable String employeeId) {
//
//        basicDetailsService.deleteBasicDetails(organizationId, employeeId);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 200);
//        response.put("message", "Employee deleted successfully");
//
//        return ResponseEntity.ok(response);
//    }
//
//    @PostMapping("/imports")
//    public ResponseEntity<Map<String, Object>> bulkUploadBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestBody List<BasicDetailsDTO> employees) {
//
//        basicDetailsService.saveAll(organizationId, employees);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 201);
//        response.put("message", "Employees imported successfully");
//        response.put("data", employees);
//
//        return ResponseEntity.ok(response);
//    }
//}
//


package com.itsdev.payroll.controller.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeDetailsDTO;
import com.itsdev.payroll.service.employee.BasicDetailsService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class BasicDetailsController {

    private final BasicDetailsService basicDetailsService;
    
	 private static final Logger log = LoggerFactory.getLogger(BasicDetailsController.class);

    public BasicDetailsController(BasicDetailsService basicDetailsService) {
        this.basicDetailsService = basicDetailsService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody BasicDetailsDTO dto) {

        BasicDetailsDTO created = basicDetailsService.createBasicDetails(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Employee created successfully");
        response.put("data", created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> updateBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @Valid @RequestBody BasicDetailsDTO dto) {

        BasicDetailsDTO updated = basicDetailsService.updateBasicDetails(organizationId, employeeId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        BasicDetailsDTO dto = basicDetailsService.getBasicDetails(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

//    @GetMapping
//    public ResponseEntity<Map<String, Object>> getAllBasicDetails(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestParam(value = "page", defaultValue = "0") int page,
//            @RequestParam(value = "size", defaultValue = "20") int size,
//            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
//            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {
//
//        int maxSize = 100;
//        int pageIndex = Math.max(0, page);
//        int pageSize = Math.max(1, Math.min(size, maxSize));
//
//        Sort sort = "desc".equalsIgnoreCase(sortDir)
//                ? Sort.by(sortBy).descending()
//                : Sort.by(sortBy).ascending();
//        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
//
//        Page<BasicDetailsDTO> result = basicDetailsService.getAllBasicDetails(organizationId, pageable);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 200);
//        response.put("message", "Employees retrieved successfully");
//        response.put("data", result.getContent());
//        response.put("page", result.getNumber());
//        response.put("size", result.getSize());
//        response.put("totalElements", result.getTotalElements());
//        response.put("totalPages", result.getTotalPages());
//
//        return ResponseEntity.ok(response);
//    }


    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(value = "workLocationId", required = false) String workLocationId,
            @RequestParam(value = "departmentId", required = false) String departmentId,
            @RequestParam(value = "designationId", required = false) String designationId
    ) {

        int maxSize = 100;
        int pageIndex = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, maxSize));

        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        Page<BasicDetailsDTO> result = basicDetailsService.getAllBasicDetails(
                organizationId, pageable, workLocationId, departmentId, designationId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employees retrieved successfully");
        response.put("data", result.getContent());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());

        return ResponseEntity.ok(response);
    }





    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> deleteBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        basicDetailsService.deleteBasicDetails(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> bulkUploadBasicDetails(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<BasicDetailsDTO> employees) {

        basicDetailsService.saveAll(organizationId, employees);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Employees imported successfully");
        response.put("data", employees);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/by-email")
    public ResponseEntity<Map<String, Object>> getEmployeeByWorkMail(@RequestParam String email) {
        String method = "getEmployeeByWorkMail";
        log.info("[{}] 📥 Incoming request with email: {}", method, email);

        EmployeeDetailsDTO dto = basicDetailsService.getEmployeeByWorkMail(email);

        log.info("[{}] ✅ Successfully fetched employee details for email: {}", method, email);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee details fetch successfully");
        response.put("data", dto);
        return ResponseEntity.ok(response);
      
    }



    @PostMapping("/imports/statutory")
    public ResponseEntity<Map<String, Object>> importStatutoryDetails(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<BasicDetailsDTO> employees) {

        basicDetailsService.importStatutory(organizationId, employees);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "Statutory details imported successfully");


        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }







}

