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
 * Controller exposing REST endpoints for Notice Period Pay, Shortfall Recovery, and Waivers (W-90.2).
 */
@RestController
@RequestMapping({"/api/v1/payroll/settlement/notice-period", "/api/v1/payroll/notice-period"})
@RequiresModule(PlatformModule.PAYROLL)
public class NoticePeriodController {

    private final NoticePeriodService service;

    public NoticePeriodController(NoticePeriodService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Calculates notice period shortfall, buyout, or waiver without persisting.
     */
    @PostMapping("/calculate")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<NoticePeriodResponse>> calculateNoticePeriod(
            @Valid @RequestBody NoticePeriodCalculateRequest request) {
        NoticePeriodResponse response = service.calculateNoticePeriod(request);
        return ResponseEntity.ok(ApiResponse.ok("Notice period settlement calculated successfully", response));
    }

    /**
     * Records and persists a notice period settlement row for the exiting employee.
     */
    @PostMapping("/record")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<ApiResponse<NoticePeriodResponse>> recordSettlement(
            @Valid @RequestBody RecordNoticeSettlementRequest request) {
        NoticePeriodResponse response = service.recordSettlement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Notice period settlement recorded successfully", response));
    }

    /**
     * Retrieves all recorded notice period settlements for an employee.
     */
    @GetMapping("/{employeeId}")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<ApiResponse<List<NoticePeriodResponse>>> getSettlements(@PathVariable UUID employeeId) {
        List<NoticePeriodResponse> responses = service.getSettlements(employeeId);
        return ResponseEntity.ok(ApiResponse.ok("Notice period settlements retrieved successfully", responses));
    }
}
