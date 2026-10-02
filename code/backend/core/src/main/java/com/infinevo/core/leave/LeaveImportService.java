package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for bulk leave allocation imports (W-16.4b, spec section 4).
 */
public interface LeaveImportService {

    /**
     * Executes bulk leave allocation import from an uploaded CSV document.
     *
     * @param tenantId the active tenant
     * @param documentId the uploaded source CSV document
     * @param leaveYear leave year string (e.g. "2026", "2026-2027")
     * @param dryRun if true, validates only without creating allocations
     * @return summary result of the import run
     */
    LeaveImportResultResponse importLeaves(UUID tenantId, UUID documentId, String leaveYear, boolean dryRun);

    default LeaveImportResultResponse importLeaves(UUID documentId, String leaveYear, boolean dryRun) {
        return importLeaves(TenantContext.require(), documentId, leaveYear, dryRun);
    }

    /**
     * Retrieves status and counts of a past import run.
     */
    LeaveImportResultResponse getImport(UUID tenantId, UUID importId);

    default LeaveImportResultResponse getImport(UUID importId) {
        return getImport(TenantContext.require(), importId);
    }

    /**
     * Lists history of bulk import runs for the tenant.
     */
    Page<LeaveImportResultResponse> listImports(UUID tenantId, Pageable pageable);

    default Page<LeaveImportResultResponse> listImports(Pageable pageable) {
        return listImports(TenantContext.require(), pageable);
    }
}
