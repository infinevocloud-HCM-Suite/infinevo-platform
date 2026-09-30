package com.infinevo.core.leave;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller for bulk leave allocation imports (W-16.4b, spec section 4).
 */
@RestController
public class LeaveImportController {

    private final LeaveImportService leaveImportService;

    public LeaveImportController(LeaveImportService leaveImportService) {
        this.leaveImportService = Objects.requireNonNull(leaveImportService, "leaveImportService must not be null");
    }

    @PostMapping("/api/v1/leave-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresAction("core.leave_balance.manage")
    public LeaveImportResultResponse importLeaves(@RequestBody LeaveImportRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null || request.documentId() == null || request.leaveYear() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId and leaveYear must be provided");
        }
        return leaveImportService.importLeaves(tenantId, request.documentId(), request.leaveYear(), request.dryRun());
    }

    @GetMapping("/api/v1/leave-imports/{id}")
    @RequiresAction("core.leave_balance.manage")
    public LeaveImportResultResponse getImport(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        try {
            return leaveImportService.getImport(tenantId, id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/api/v1/leave-imports")
    @RequiresAction("core.leave_balance.manage")
    public Page<LeaveImportResultResponse> listImports(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        return leaveImportService.listImports(tenantId, pageable);
    }
}
