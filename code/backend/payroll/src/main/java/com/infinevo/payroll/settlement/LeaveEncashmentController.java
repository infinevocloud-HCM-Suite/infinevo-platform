package com.infinevo.payroll.settlement;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import jakarta.validation.Valid;
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
 * Controller exposing REST endpoints for Leave Encashment on exit & statutory tax exemption (W-90.1).
 */
@RestController
@RequestMapping("/api/v1/payroll/settlement/leave-encashment")
@RequiresModule(PlatformModule.PAYROLL)
public class LeaveEncashmentController {

    private final LeaveEncashmentService service;

    public LeaveEncashmentController(LeaveEncashmentService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Estimates leave encashment and Section 10(10AA) exemption without persisting.
     */
    @PostMapping("/estimate")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<LeaveEncashmentResponse>> estimateEncashment(
            @Valid @RequestBody LeaveEncashmentEstimateRequest request) {
        LeaveEncashmentResponse response = service.estimateEncashment(request);
        return ResponseEntity.ok(ApiResponse.ok("Leave encashment estimate computed successfully", response));
    }

    /**
     * Records and persists a leave encashment settlement row for the exiting employee.
     */
    @PostMapping("/record")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<ApiResponse<LeaveEncashmentResponse>> recordSettlement(
            @Valid @RequestBody RecordLeaveEncashmentRequest request) {
        LeaveEncashmentResponse response = service.recordSettlement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Leave encashment settlement recorded successfully", response));
    }

    /**
     * Retrieves all recorded leave encashment settlements for an employee.
     */
    @GetMapping("/{employeeId}")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<List<LeaveEncashmentResponse>>> getSettlements(@PathVariable UUID employeeId) {
        List<LeaveEncashmentResponse> responses = service.getSettlements(employeeId);
        return ResponseEntity.ok(ApiResponse.ok("Leave encashment settlements retrieved successfully", responses));
    }
}
