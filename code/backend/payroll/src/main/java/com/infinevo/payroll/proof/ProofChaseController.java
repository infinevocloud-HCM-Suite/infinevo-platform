package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * An officer's chase list and summary of proof-of-investment status across employees (W-34.3 spec section 4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/proof-of-investment")
public class ProofChaseController extends ProofControllerSupport {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProofChaseService proofChaseService;

    public ProofChaseController(ProofChaseService proofChaseService) {
        this.proofChaseService = Objects.requireNonNull(proofChaseService, "proofChaseService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.proof.read")
    public ApiResponse<Page<ProofChaseRow>> list(
            @RequestParam(name = "fy") String financialYear,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int clampedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        Page<ProofChaseRow> result = proofChaseService.list(financialYear, status, search, pageable);
        return ApiResponse.ok("Proof of investment chase list retrieved successfully", result);
    }

    @GetMapping("/summary")
    @RequiresAction("payroll.proof.read")
    public ApiResponse<ProofChaseSummary> summary(@RequestParam(name = "fy") String financialYear) {
        ProofChaseSummary result = proofChaseService.summary(financialYear);
        return ApiResponse.ok("Proof of investment summary retrieved successfully", result);
    }
}
