package com.itsdev.payroll.controller.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.ReimbursementDTO;
import com.itsdev.payroll.service.salarycomponents.ReimbursementService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reimbursements")
public class ReimbursementController {

    private final ReimbursementService reimbursementService;

    public ReimbursementController(ReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(@RequestHeader("organizationId") String organizationId) {
        List<ReimbursementDTO> reimbursements = reimbursementService.getAllReimbursements(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursements fetched successfully");
        response.put("data", reimbursements);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reimbursementId}")
    public ResponseEntity<Map<String, Object>> getById(@RequestHeader("organizationId") String organizationId,
                                                       @PathVariable("reimbursementId") String reimbursementId) {
        ReimbursementDTO dto = reimbursementService.getReimbursement(organizationId, reimbursementId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestHeader("organizationId") String organizationId,
    		@Valid @RequestBody ReimbursementDTO dto) {
        ReimbursementDTO created = reimbursementService.createReimbursement(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Reimbursement created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{reimbursementId}")
    public ResponseEntity<Map<String, Object>> update(@RequestHeader("organizationId") String organizationId,
                                                      @PathVariable("reimbursementId") String reimbursementId,
                                                      @Valid @RequestBody ReimbursementDTO dto) {
                                                      
        ReimbursementDTO updated = reimbursementService.updateReimbursement(organizationId, reimbursementId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reimbursementId}")
    public ResponseEntity<Map<String, Object>> delete(@RequestHeader("organizationId") String organizationId,
                                                      @PathVariable("reimbursementId") String reimbursementId) {
        reimbursementService.deleteReimbursement(organizationId, reimbursementId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.NO_CONTENT.value());
        response.put("message", "Reimbursement deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/inactive/{reimbursementId}")
    public ResponseEntity<Map<String, Object>> inactivate(@RequestHeader("organizationId") String organizationId,
                                                          @PathVariable("reimbursementId") String reimbursementId) {
        reimbursementService.inactivateReimbursement(organizationId, reimbursementId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement inactivated successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/active/{reimbursementId}")
    public ResponseEntity<Map<String, Object>> reactivate(@RequestHeader("organizationId") String organizationId,
                                                          @PathVariable("reimbursementId") String reimbursementId) {
        reimbursementService.reactivateReimbursement(organizationId, reimbursementId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Reimbursement reactivated successfully");

        return ResponseEntity.ok(response);
    }
}
