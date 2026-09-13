package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.IncomeTaxDetailsDTO;
import com.itsdev.payroll.service.organization.IncomeTaxDetailsService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/income-tax-details")
public class IncomeTaxDetailsController {

    private final IncomeTaxDetailsService service;

    public IncomeTaxDetailsController(IncomeTaxDetailsService service) {
        this.service = service;
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateIncomeTaxDetails(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody IncomeTaxDetailsDTO dto) {

        IncomeTaxDetailsDTO updated = service.update(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Income Tax Details updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getIncomeTaxDetails(
            @RequestHeader("organizationId") String organizationId) {

        IncomeTaxDetailsDTO details = service.get(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Income Tax Details fetched successfully");
        response.put("data", details);

        return ResponseEntity.ok(response);
    }
}