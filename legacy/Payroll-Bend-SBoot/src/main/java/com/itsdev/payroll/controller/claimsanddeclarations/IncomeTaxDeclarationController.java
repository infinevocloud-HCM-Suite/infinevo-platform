package com.itsdev.payroll.controller.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.service.claimsanddeclarations.IncomeTaxDeclarationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/income-tax-declarations")
public class IncomeTaxDeclarationController {

    private final IncomeTaxDeclarationService declarationService;

    public IncomeTaxDeclarationController(IncomeTaxDeclarationService declarationService) {
        this.declarationService = declarationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getIncomeTaxDeclaration(@RequestHeader("organizationId") String organizationId) {
        IncomeTaxDeclarationDTO dto = declarationService.getIncomeTaxDeclaration(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Income tax declaration fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateIncomeTaxDeclaration(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody IncomeTaxDeclarationDTO dto) {

        IncomeTaxDeclarationDTO updated = declarationService.updateIncomeTaxDeclaration(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Income tax declaration updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
}