package com.itsdev.payroll.controller.taxCalculator;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabCreateRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabResponseDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabSaveRequest;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.TaxSlabService;
import com.itsdev.payroll.util.JWTUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST entry point for the "TaxSlab Regim" tab.
 *
 *   GET  /api/tax-slabs        -> all active regimes (OLD + NEW) for the cards
 *   PUT  /api/tax-slabs/{id}   -> validate + save edits for one regime (in place)
 *
 * Response envelope matches the rest of the project: { status, message, data }.
 * Validation failures from the service surface as HTTP 400 with a clear message
 * the UI shows via errorMsg(...).
 */
@RestController
@RequestMapping("/api/tax-slabs")
public class TaxSlabMasterController {

    private static final Logger log = LoggerFactory.getLogger(TaxSlabMasterController.class);

    private final TaxSlabService taxSlabService;

    public TaxSlabMasterController(TaxSlabService taxSlabService) {
        this.taxSlabService = taxSlabService;
    }

    // ---------------------------------------------------------------------
    // GET all active slab regimes
    // ---------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<Map<String, Object>> getActiveTaxSlabs() {
        log.info("📥 Fetching active tax slab regimes");

        List<TaxSlabResponseDTO> slabs = taxSlabService.getActiveSlabs();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Tax slabs fetched successfully");
        response.put("data", slabs);
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    // PUT update one regime's slabs (validated + audited inside the service)
    // ---------------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTaxSlabs(
            @PathVariable Long id,
            @RequestBody TaxSlabSaveRequest request) {

        // Who is making the change (for the audit row) — pulled from the JWT.
        String changedBy = getCurrentUserId();

        log.info("📥 Update tax slabs request | id={}, by={}", id, changedBy);

        try {
            TaxSlabResponseDTO updated = taxSlabService.updateSlabs(id, request, changedBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Tax slabs updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            // Validation / not-found errors -> 400 with the human-readable reason.
            log.warn("⛔ Tax slab update rejected | id={}, reason={}", id, ex.getMessage());

            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", HttpStatus.BAD_REQUEST.value());
            error.put("message", ex.getMessage());
            error.put("data", null);
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ---------------------------------------------------------------------
    // POST create a new regime (new FY or new regime type)
    // ---------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTaxRegime(
            @RequestBody TaxSlabCreateRequest request) {

        String createdBy = getCurrentUserId();
        log.info("📥 Create tax regime request | FY={}, regime={}, by={}",
                request.getFinancialYear(), request.getTaxRegime(), createdBy);

        try {
            TaxSlabResponseDTO created = taxSlabService.createRegime(request, createdBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.CREATED.value());
            response.put("message", "Tax regime created successfully");
            response.put("data", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException ex) {
            log.warn("⛔ Tax regime create rejected | reason={}", ex.getMessage());

            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", HttpStatus.BAD_REQUEST.value());
            error.put("message", ex.getMessage());
            error.put("data", null);
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ---------------------------------------------------------------------
    // DELETE (soft) deactivate a regime
    // ---------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTaxRegime(@PathVariable Long id) {

        String deletedBy = getCurrentUserId();
        log.info("📥 Delete tax regime request | id={}, by={}", id, deletedBy);

        try {
            taxSlabService.deleteRegime(id, deletedBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "Tax regime deactivated successfully");
            response.put("data", null);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            log.warn("⛔ Tax regime delete rejected | id={}, reason={}", id, ex.getMessage());

            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", HttpStatus.BAD_REQUEST.value());
            error.put("message", ex.getMessage());
            error.put("data", null);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /** userId from the JWT; null-safe so audit still works if claim missing. */
    private String getCurrentUserId() {
        try {
            return JWTUtil.getUserIdAndEmailFromToken().get("userId");
        } catch (Exception e) {
            return null;
        }
    }
}
