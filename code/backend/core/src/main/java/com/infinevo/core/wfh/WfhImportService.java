package com.infinevo.core.wfh;

import java.util.List;
import java.util.UUID;

public interface WfhImportService {

    byte[] generateSampleCsv();

    WfhImportSummaryResponse importAllocations(
            UUID tenantId, String fileName, String wfhYear, byte[] csvBytes, boolean dryRun);

    List<WfhImportLog> listImports(UUID tenantId);
}
