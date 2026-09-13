package com.itsdev.payroll.controller.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.ProfessionalTaxDTO;
import com.itsdev.payroll.dto.statutorycomponents.TaxSlabUpdateRequest;
import com.itsdev.payroll.service.statutorycomponents.ProfessionalTaxService;
import com.itsdev.payroll.serviceimpl.statutorycomponents.ProfessionalTaxServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/professional-tax")
public class ProfessionalTaxController {

    private final ProfessionalTaxService professionalTaxService;

	 private static final Logger log = LoggerFactory.getLogger(ProfessionalTaxController.class);

    public ProfessionalTaxController(ProfessionalTaxService professionalTaxService) {
        this.professionalTaxService = professionalTaxService;
    }

//    @GetMapping
//    public ResponseEntity<ProfessionalTaxDTO> getProfessionalTax(@RequestHeader("organizationId") String organizationId) {
//        return ResponseEntity.ok(professionalTaxService.getProfessionalTax(organizationId));
//    }

    @PutMapping("/{taxId}")
    public ResponseEntity<Map<String, Object>>  updateProfessionalTax(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String taxId,
            @RequestBody ProfessionalTaxDTO dto) {

        ProfessionalTaxDTO updated = professionalTaxService.updateProfessionalTax(organizationId, taxId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Professional Tax updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProfessionalTaxes(
            @RequestHeader("organizationId") String organizationId) {

        List<ProfessionalTaxDTO> taxes = professionalTaxService.getAllProfessionalTaxes(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Professional Taxes fetched successfully");
        response.put("data", taxes);

        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{taxId}/slab")
    public ResponseEntity<Map<String, Object>> updateTaxSlabAndEffectiveDate(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String taxId,
            @RequestBody TaxSlabUpdateRequest dto) {

        String method = "updateTaxSlabAndEffectiveDate";

        log.info("[{}] 📥 Incoming request to update tax slab & effective date | orgId={}, taxId={}, payload={}",
                method, organizationId, taxId, dto);


        ProfessionalTaxDTO updated =
                professionalTaxService.updateSlabAndEffectiveDate(organizationId, taxId, dto);

        log.info("[{}] ✅ Update completed successfully | orgId={}, taxId={}", 
                method, organizationId, taxId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Slab & Effective Date updated successfully");
        response.put("data", updated);

        log.info("[{}] 📤 Sending response back to client | orgId={}, taxId={}",
                method, organizationId, taxId);

        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{taxId}/slab/reset")
    public ResponseEntity<Map<String, Object>> resetToDefaultSlabs(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String taxId) {

        String method = "resetToDefaultSlabs";
        log.info("[{}] 🧹 Reset request | orgId={}, taxId={}", method, organizationId, taxId);

        ProfessionalTaxDTO updated =
                professionalTaxService.resetToDefaultSlabs(organizationId, taxId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reset to default slabs completed");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }



    
    

}