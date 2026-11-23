package com.infinevo.payroll.settlement;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Full & Final (F&F) Settlement Computation & Lifecycle (W-90.3).
 */
@RestController
@RequestMapping({"/api/v1/payroll/settlement/fnf", "/api/v1/payroll/fnf"})
@RequiresModule(PlatformModule.PAYROLL)
public class FnfSettlementController {

    private final FnfSettlementService service;

    public FnfSettlementController(FnfSettlementService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Initiates and calculates a consolidated draft F&F settlement.
     */
    @PostMapping("/initiate")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<ApiResponse<FnfSettlementResponse>> initiateSettlement(
            @Valid @RequestBody FnfInitiateRequest request) {
        FnfSettlementResponse response = service.initiateSettlement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("F&F settlement initiated successfully", response));
    }

    /**
     * Retrieves a specific F&F settlement by ID.
     */
    @GetMapping("/{id}")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<FnfSettlementResponse>> getSettlement(@PathVariable UUID id) {
        FnfSettlementResponse response = service.getSettlement(id);
        return ResponseEntity.ok(ApiResponse.ok("F&F settlement retrieved successfully", response));
    }

    /**
     * Lists all settlements for a specific employee.
     */
    @GetMapping("/employee/{employeeId}")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<List<FnfSettlementResponse>>> getSettlementsByEmployee(
            @PathVariable UUID employeeId) {
        List<FnfSettlementResponse> responses = service.getSettlementsByEmployee(employeeId);
        return ResponseEntity.ok(ApiResponse.ok("Employee F&F settlements retrieved successfully", responses));
    }

    /**
     * Lists all F&F settlements in the current tenant.
     */
    @GetMapping
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<List<FnfSettlementResponse>>> listSettlements() {
        List<FnfSettlementResponse> responses = service.listSettlements();
        return ResponseEntity.ok(ApiResponse.ok("F&F settlements listed successfully", responses));
    }

    /**
     * Approves an F&F settlement in DRAFT status.
     */
    @PostMapping("/{id}/approve")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<ApiResponse<FnfSettlementResponse>> approveSettlement(
            @PathVariable UUID id, Principal principal) {
        String approver = principal != null ? principal.getName() : "admin";
        FnfSettlementResponse response = service.approveSettlement(id, approver);
        return ResponseEntity.ok(ApiResponse.ok("F&F settlement approved successfully", response));
    }

    /**
     * Marks an approved F&F settlement as PAID.
     */
    @PostMapping("/{id}/pay")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<ApiResponse<FnfSettlementResponse>> paySettlement(@PathVariable UUID id) {
        FnfSettlementResponse response = service.paySettlement(id);
        return ResponseEntity.ok(ApiResponse.ok("F&F settlement marked as paid successfully", response));
    }
}
