package com.infinevo.payroll.statutory.ecr;

import java.util.UUID;

/**
 * Service defining operations for monthly EPFO Electronic Challan cum Return (ECR 2.0) file generation (W-87).
 */
public interface PfEcrService {

    /**
     * Generates in-app summary preview and validation warnings for an approved or computed regular pay run.
     */
    PfEcrPreviewResponse getEcrPreview(UUID payrunId);

    /**
     * Generates the downloadable ECR file in TXT, XLSX, or CSV format.
     */
    PfEcrFile exportEcrFile(UUID payrunId, PfEcrFormat format);
}
