package com.itsdev.payroll.controller.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationRequestDTO;
import com.itsdev.payroll.service.employeeitdeclaration.EmployeeInvestmentDeclarationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employee-it-declarations")
public class EmployeeInvestmentDeclarationController {

    private final EmployeeInvestmentDeclarationService declarationService;

    public EmployeeInvestmentDeclarationController(
            EmployeeInvestmentDeclarationService declarationService
    ) {
        this.declarationService = declarationService;
    }

    // ======================= CREATE =======================

    @PostMapping("/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> createDeclaration(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @RequestBody EmployeeInvestmentDeclarationRequestDTO dto
    ) {
        EmployeeInvestmentDeclarationDTO created =
                declarationService.createDeclaration(organizationId, employeeId, fiscalYear, dto);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CREATED.value());
        body.put("message", "Employee IT declaration created successfully");
        body.put("data", created);

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ======================= READ =======================

    @GetMapping("/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> getDeclaration(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear
    ) {
        EmployeeInvestmentDeclarationDTO dto =
                declarationService.getDeclaration(organizationId, employeeId, fiscalYear);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.OK.value());
        body.put("message", "Employee IT declaration fetched successfully");
        body.put("data", dto);

        return ResponseEntity.ok(body);
    }

    // ======================= UPDATE =======================

    @PutMapping("/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> updateDeclaration(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear,
            @RequestBody EmployeeInvestmentDeclarationRequestDTO dto
    ) {
        EmployeeInvestmentDeclarationDTO updated =
                declarationService.updateDeclaration(organizationId, employeeId, fiscalYear, dto);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.OK.value());
        body.put("message", "Employee IT declaration updated successfully");
        body.put("data", updated);

        return ResponseEntity.ok(body);
    }

    // ======================= DELETE =======================

    @DeleteMapping("/{employeeId}/{fiscalYear}")
    public ResponseEntity<Map<String, Object>> deleteDeclaration(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("fiscalYear") Integer fiscalYear
    ) {
        declarationService.deleteDeclaration(organizationId, employeeId, fiscalYear);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.OK.value());
        body.put("message", "Employee IT declaration deleted successfully");

        return ResponseEntity.ok(body);
    }
}
