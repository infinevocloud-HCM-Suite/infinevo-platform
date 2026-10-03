package com.infinevo.payroll.priorpayroll;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for prior payroll file imports and import history (W-38.1 §4).
 */
public interface PriorPayrollImportService {

    PriorPayrollImportResponse importFile(UUID documentId, String financialYear, boolean dryRun);

    PriorPayrollImportResponse getImport(UUID id);

    Page<PriorPayrollImportResponse> listImports(Pageable pageable);

    String template();
}
