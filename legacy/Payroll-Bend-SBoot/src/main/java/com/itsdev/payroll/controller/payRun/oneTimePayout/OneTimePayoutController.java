package com.itsdev.payroll.controller.payRun.oneTimePayout;


//import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportRequestDTO;
//import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutResponseDTO;
import com.itsdev.payroll.service.payRun.oneTImePayout.OneTimePayoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/onetime-payouts")
public class OneTimePayoutController {

    private final OneTimePayoutService payoutService;

    public OneTimePayoutController(OneTimePayoutService payoutService) {
        this.payoutService = payoutService;
    }

    /**
     * Step 1: Create a draft payout (earning + payDate, no employees yet)
     */
    @PostMapping("/draft")
    public ResponseEntity<OneTimePayoutResponseDTO> createDraftPayout(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody OneTimePayoutRequestDTO requestDto) {
        return ResponseEntity.ok(payoutService.createDraftPayout(organizationId, requestDto));
    }

    /**
     * Step 2: Add employees to payout
     */
    @PostMapping("/employees")
    public ResponseEntity<List<OneTimePayoutResponseDTO>> addEmployeesToPayout(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody OneTimePayoutRequestDTO requestDto) {
        return ResponseEntity.ok(payoutService.addEmployeesToPayout(organizationId, requestDto));
    }

    /**
     * Fetch a single payout by ID
     */
    @GetMapping("/{payoutId}")
    public ResponseEntity<OneTimePayoutResponseDTO> getPayout(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long payoutId) {
        return ResponseEntity.ok(payoutService.getPayout(organizationId, payoutId));
    }

    /**
     * Fetch all payouts for the organization
     */
    @GetMapping
    public ResponseEntity<List<OneTimePayoutResponseDTO>> getAllPayouts(
            @RequestHeader("organizationId") String organizationId) {
        return ResponseEntity.ok(payoutService.getAllPayouts(organizationId));
    }

    /**
     * Fetch all payouts for a given earning component
     */
    @GetMapping("/earning/{earningId}")
    public ResponseEntity<List<OneTimePayoutResponseDTO>> getPayoutsByEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId) {
        return ResponseEntity.ok(payoutService.getPayoutsByEarning(organizationId, earningId));
    }

    /**
     * Fetch all payouts for a given employee
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<OneTimePayoutResponseDTO>> getPayoutsByEmployee(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(payoutService.getPayoutsByEmployee(organizationId, employeeId));
    }

    /**
     * Delete a payout
     */
    @DeleteMapping("/{payoutId}")
    public ResponseEntity<Void> deletePayout(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long payoutId) {
        payoutService.deletePayout(organizationId, payoutId);
        return ResponseEntity.noContent().build();
    }

//    @PostMapping("/import")
//    public ResponseEntity<List<OneTimePayoutResponseDTO>> importEmployeesToPayout(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestBody OneTimePayoutImportRequestDTO requestDto) {
//        return ResponseEntity.ok(payoutService.importEmployeesToPayout(organizationId, requestDto));
//    }
// ✅ New endpoint for import
// ✅ Import employees to payout (avoid conflict with /{id})
@PostMapping("/import")
public ResponseEntity<List<OneTimePayoutResponseDTO>> importEmployeesToPayout(
        @RequestHeader("organizationId") String organizationId,
        @RequestBody OneTimePayoutImportRequestDTO requestDto) {

    List<OneTimePayoutResponseDTO> response =
            payoutService.importEmployeesToPayout(organizationId, requestDto);

    return ResponseEntity.ok(response);
}








    /**
     * Update a payout (amount, payDate, taxes)
     */
    @PutMapping("/{payoutId}")
    public ResponseEntity<OneTimePayoutResponseDTO> updatePayout(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long payoutId,
            @Valid @RequestBody OneTimePayoutRequestDTO requestDto) {
        return ResponseEntity.ok(payoutService.updatePayout(organizationId, payoutId, requestDto));
    }



}

