package com.itsdev.payroll.controller.payruns;

import com.itsdev.payroll.dto.payruns.PayRunDTO;
import com.itsdev.payroll.dto.payruns.PersistedPayRunDTO;
import com.itsdev.payroll.service.payruns.PayRunService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payruns")
public class PayRunController {

    private final PayRunService payRunService;
    
    private static final Logger log = LoggerFactory.getLogger(PayRunController.class);

    public PayRunController(PayRunService payRunService) {
        this.payRunService = payRunService;
    }

    @GetMapping
    public ResponseEntity<?> getAllPayRuns(@RequestHeader("organizationId") String organizationId) {
        Map<String, Object> resp = payRunService.getAllPayRunsStructuredResponse(organizationId);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{payrunId}")
    public ResponseEntity<?> getPayRun(@RequestHeader("organizationId") String organizationId,
                                       @PathVariable("payrunId") String payrunId) {
        PayRunDTO dto = payRunService.getPayRunById(organizationId, payrunId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "Pay run fetched successfully");
        resp.put("payrollRun", dto);
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<?> createPayRun(@RequestHeader("organizationId") String organizationId,
                                          @RequestBody PayRunDTO dto) {
        PayRunDTO created = payRunService.createPayRun(organizationId, dto);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.CREATED.value());
        resp.put("message", "Pay run created successfully");
        resp.put("payrollRun", created);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{payrunId}")
    public ResponseEntity<?> updatePayRun(@RequestHeader("organizationId") String organizationId,
                                          @PathVariable("payrunId") String payrunId,
                                          @RequestBody PayRunDTO dto) {
        PayRunDTO updated = payRunService.updatePayRun(organizationId, payrunId, dto);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "Pay run updated successfully");
        resp.put("payrollRun", updated);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/completed")
    public ResponseEntity<?> getAllCompletedPayRuns(@RequestHeader("organizationId") String organizationId) {
        List<PersistedPayRunDTO> completedList = payRunService.getAllCompletedPayRuns(organizationId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "success");
        resp.put("payrollRuns", completedList);

        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{payrunId}/approve")
    public ResponseEntity<?> approvePayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId,
            @RequestParam("canPostPayrunTransactions") boolean canPostPayrunTransactions
    ) {
        PayRunDTO updated = payRunService.approvePayRun(organizationId, payrunId, canPostPayrunTransactions);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "Pay run has been approved");
        resp.put("payrollRun", updated);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{payrunId}/reject")
    public ResponseEntity<?> rejectPayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId,
            @RequestParam("rejectedReason") String rejectedReason
    ) {
        PayRunDTO updated = payRunService.rejectPayRun(organizationId, payrunId, rejectedReason);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "Pay run has been Rejected");
        resp.put("payrollRun", updated);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{payrunId}/payment")
    public ResponseEntity<?> completePayRunPayment(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId,
            @RequestBody List<String> employeeIds) {

        payRunService.completePayRunPayment(organizationId, payrunId, employeeIds);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Payment completed successfully for selected employees.");
        return ResponseEntity.ok(response);
    }
    

    @DeleteMapping("/{payrunId}")
    public ResponseEntity<?> deletePayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId
    ) {
        String method = "deletePayRun";
        log.info("[{}] 🗑️ Received request to delete PayRun with ID: {} for Organization: {}", method, payrunId, organizationId);

        payRunService.deletePayRun(organizationId, payrunId);

        log.info("[{}] ✅ PayRun deleted successfully (payrunId={})", method, payrunId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", HttpStatus.OK.value());
        resp.put("message", "Pay run has been deleted successfully");

        return ResponseEntity.ok(resp);
    }




}
