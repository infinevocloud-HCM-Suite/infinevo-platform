package com.itsdev.payroll.controller.payruns;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.itsdev.payroll.dto.payruns.EmployeePayRunDTO;
import com.itsdev.payroll.dto.payruns.EmployeePayslipDTO;
import com.itsdev.payroll.dto.payruns.PayslipResponseDTO;
import com.itsdev.payroll.service.payruns.EmployeePayRunService;

@RestController
@RequestMapping("/api/payrun-employees")
public class EmployeePayRunController {


    private final EmployeePayRunService employeeService;
    private static final Logger log = LoggerFactory.getLogger(EmployeePayRunController.class);

    public EmployeePayRunController(EmployeePayRunService employeeService) {
        this.employeeService = employeeService;
    }

 @GetMapping("/employee-list")
 public ResponseEntity<Map<String, Object>> getEmployeePayRunList(
         @RequestHeader("organizationId") String organizationId,
         @RequestParam(required = false) String processingPeriod) {

     String method = "getEmployeePayRunList";
     log.info("[{}] 📥 Incoming request to fetch employee pay run list | orgId={} | processingPeriod={}", method, organizationId, processingPeriod );

     List<EmployeePayRunDTO> employeeList = employeeService.getEmployeePayRunList(organizationId, processingPeriod);

     log.info("[{}] ✅ Employee pay run list fetched successfully | orgId={} | totalEmployees={}", 
              method, organizationId, employeeList.size());

     Map<String, Object> response = new LinkedHashMap<>();
     response.put("status", 200);
     response.put("message", "Employee pay run list retrieved successfully");
     response.put("employees", employeeList);

     return ResponseEntity.ok(response);
 }
 
 /*
 @PostMapping("/generate/{payrunId}")
 public ResponseEntity<Map<String, Object>> generateEmployeePayRuns(
         @RequestHeader("organizationId") String organizationId,
         @PathVariable String payrunId) {

     String method = "generateEmployeePayRuns";
     log.info("[{}] 📥 Request received to generate employee pay runs | orgId={} | payrunId={}", 
              method, organizationId, payrunId);

     List<EmployeePayRunDTO> createdList = employeeService.generateEmployeePayRuns(organizationId, payrunId);

     Map<String, Object> response = new LinkedHashMap<>();
     response.put("status", 201);
     response.put("message", "Employee pay run entries generated successfully");
     response.put("employees", createdList);

     log.info("[{}] ✅ Generated {} employee pay run entries for payrunId={}", 
              method, createdList.size(), payrunId);

     return ResponseEntity.status(HttpStatus.CREATED).body(response);
 } */
 
 @GetMapping("/list/{payrunId}")
 public ResponseEntity<Map<String, Object>> getEmployeePayRunListByPayRun(
         @RequestHeader("organizationId") String organizationId,
         @PathVariable String payrunId) {

     String method = "getEmployeePayRunListByPayRun";
     log.info("[{}] 📥 Request received to fetch employee pay runs | orgId={} | payrunId={}", 
              method, organizationId, payrunId);

     List<EmployeePayRunDTO> employeeList = employeeService.getEmployeePayRunListByPayRun(organizationId, payrunId);

     Map<String, Object> response = new LinkedHashMap<>();
     response.put("status", 200);
     response.put("message", "Employee pay run list retrieved successfully");
     response.put("employees", employeeList);

     log.info("[{}] ✅ Fetched {} employee pay run entries for payrunId={}", 
              method, employeeList.size(), payrunId);

     return ResponseEntity.ok(response);
 }
 
  
 
 @GetMapping("/payslips")
 public ResponseEntity<Map<String, Object>> getEmployeePayslips(
	     @RequestHeader("organizationId") String organizationId,
         @RequestParam String employeeId,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size,
         @RequestParam(required = false) Integer year) {

     String method = "getEmployeePayslips";
     log.info("[{}] 📥 Request received | orgId={} | empId={} | page={} | size={} | year={}", 
              method, organizationId, employeeId, page, size, year);

     Page<EmployeePayslipDTO> payslipPage = employeeService.getEmployeePayslips(
             organizationId, employeeId, page, size, year);

     Map<String, Object> response = new LinkedHashMap<>();
     response.put("status", 200);
     response.put("message", "Employee payslips retrieved successfully");
     response.put("payslips", payslipPage.getContent()); 

     // Optional pagination info
     Map<String, Object> pageInfo = new LinkedHashMap<>();
     pageInfo.put("page", payslipPage.getNumber() + 1);
     pageInfo.put("size", payslipPage.getSize());
     pageInfo.put("totalPages", payslipPage.getTotalPages());
     pageInfo.put("totalElements", payslipPage.getTotalElements());
     pageInfo.put("hasNext", payslipPage.hasNext());
     response.put("pageContext", pageInfo);

     log.info("[{}] ✅ Fetched {} payslips for employeeId={}", method, payslipPage.getNumberOfElements(), employeeId);

     return ResponseEntity.ok(response);
 }
 
 @GetMapping("/{payrunId}/{employeeId}")
 public ResponseEntity<Map<String, Object>> getPayslip(
         @RequestHeader("organizationId") String organizationId,
         @PathVariable String payrunId,
         @PathVariable String employeeId) {

     String method = "getPayslip";
     log.info("[{}] 📥 Request received to fetch payslip | orgId={} | payrunId={} | employeeId={}",
             method, organizationId, payrunId, employeeId);

     PayslipResponseDTO payslipResponse = employeeService.getEmployeePayslip(organizationId, employeeId, payrunId);

     Map<String, Object> response = new LinkedHashMap<>();
     response.put("status", 200);
     response.put("message", "Payslip retrieved successfully");
     response.put("data", payslipResponse);

     log.info("[{}] ✅ Payslip fetched successfully for employeeId={} | payrunId={}",
             method, employeeId, payrunId);

     return ResponseEntity.ok(response);
 }

    @PostMapping("/{payrunId}/download")
    public ResponseEntity<byte[]> downloadEmployeePayRunCsv(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId,
            @RequestBody List<String> employeeIds) {

        byte[] csvData = employeeService.generateEmployeePayRunCsv(organizationId, payrunId, employeeIds);

        String fileName = "employee_payruns_" + payrunId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }


}

