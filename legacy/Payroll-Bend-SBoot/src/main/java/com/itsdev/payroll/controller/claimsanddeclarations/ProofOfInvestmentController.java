package com.itsdev.payroll.controller.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ProofOfInvestmentDTO;
import com.itsdev.payroll.service.claimsanddeclarations.ProofOfInvestmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/proof-of-investment")
public class ProofOfInvestmentController {

    private final ProofOfInvestmentService poiService;

    public ProofOfInvestmentController(ProofOfInvestmentService poiService) {
        this.poiService = poiService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProofOfInvestment(@RequestHeader("organizationId") String organizationId) {
        ProofOfInvestmentDTO dto = poiService.getProofOfInvestment(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Proof of investment fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProofOfInvestment(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody ProofOfInvestmentDTO dto) {

        ProofOfInvestmentDTO updated = poiService.updateProofOfInvestment(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Proof of investment updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
}
