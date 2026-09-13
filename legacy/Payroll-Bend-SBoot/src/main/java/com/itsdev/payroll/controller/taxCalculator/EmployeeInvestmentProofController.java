package com.itsdev.payroll.controller.taxCalculator;


import com.itsdev.payroll.controller.employee.EmployyePortalContoller;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofRequest;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofResponse;
import com.itsdev.payroll.dto.taxCalculator.POIDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.POISubmissionRequest;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import com.itsdev.payroll.repository.taxCalculator.ProofOfInvestmentDocumentRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.taxCalculator.EmployeeInvestmentProofService;
import com.itsdev.payroll.service.taxCalculator.POIService;
import com.itsdev.payroll.util.JWTUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/employee-investment-proof")
public class EmployeeInvestmentProofController {

    private final EmployeeInvestmentProofService proofService;
    
	 private static final Logger log = LoggerFactory.getLogger(EmployeeInvestmentProofController.class);

    public EmployeeInvestmentProofController(EmployeeInvestmentProofService proofService) {
        this.proofService = proofService;
    }

    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadProof(
            @RequestHeader("organizationId") String organizationId,
            @ModelAttribute EmployeeInvestmentProofRequest request
    ) throws IOException {

        String method = "uploadProof";

        log.info("[{}] 📥 Incoming request to upload investment proof | orgId={}, employeeId={}", 
                method, organizationId, request.getEmployeeId());

        proofService.submitInvestmentProof(request, organizationId);

        log.info("[{}] ✅ Investment proof uploaded successfully | orgId={}, employeeId={}", 
                method, organizationId, request.getEmployeeId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee investment proof submitted successfully");

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getEmployeeInvestmentProof(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId,
            @RequestParam Integer financialYear  
    ) {

        String method = "getEmployeeInvestmentProof";

        log.info("[{}] 📥 Fetching investment proof | orgId={}, employeeId={}, financialYear={}", 
                method, organizationId, employeeId, financialYear);


        EmployeeInvestmentProofResponse data =
                proofService.getProofByEmployeeId(employeeId, organizationId, financialYear);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Investment proof fetched successfully");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }



}
