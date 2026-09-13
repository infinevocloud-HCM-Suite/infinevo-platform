package com.itsdev.payroll.controller.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReimbursementClaimDTO;
import com.itsdev.payroll.service.claimsanddeclarations.ReimbursementClaimService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reimbursement-claims")
public class ReimbursementClaimController {

    private final ReimbursementClaimService claimService;

    public ReimbursementClaimController(ReimbursementClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReimbursementClaim(
            @RequestHeader("organizationId") String organizationId) {
        ReimbursementClaimDTO claim = claimService.getReimbursementClaim(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement claim fetched successfully");
        response.put("data", claim);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateReimbursementClaim(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody ReimbursementClaimDTO dto) {
        ReimbursementClaimDTO updated = claimService.updateReimbursementClaim(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement claim updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
}
