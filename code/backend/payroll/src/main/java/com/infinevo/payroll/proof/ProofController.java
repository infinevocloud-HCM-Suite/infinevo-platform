package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An officer's read of any employee's proof of investment (W-34.1 spec section 4). Read only: it never
 * creates a proof, never syncs its items and never changes a status.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/employees/{employeeId}/proof-of-investment")
public class ProofController extends ProofControllerSupport {

    private final ProofService proofService;

    public ProofController(ProofService proofService) {
        this.proofService = Objects.requireNonNull(proofService, "proofService must not be null");
    }

    @GetMapping("/{fy}")
    @RequiresAction("payroll.proof.read")
    public ApiResponse<ProofResponse> get(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        return ApiResponse.ok(
                "Proof of investment retrieved successfully", proofService.read(employeeId, financialYear));
    }

    @GetMapping("/{fy}/items/{itemId}/documents/{documentId}")
    @RequiresAction("payroll.proof.read")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @PathVariable("documentId") UUID documentId) {
        return file(proofService.openDocument(employeeId, financialYear, itemId, documentId));
    }
}
