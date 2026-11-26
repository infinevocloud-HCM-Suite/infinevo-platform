package com.infinevo.payroll.compliance.form24q;

/**
 * Service for Form 24Q Quarterly Deductee Mapping & Annexure I Generation (W-89.2).
 */
public interface Form24QAnnexure1Service {

    /**
     * Previews the Annexure I dataset, reconciliation status, and missing PAN count.
     *
     * @param financialYear Indian FY format (e.g. "2026-2027")
     * @param quarter quarter code ("Q1", "Q2", "Q3", or "Q4")
     * @return preview metadata and record list
     */
    Annexure1PreviewResponse previewAnnexure1(String financialYear, String quarter);

    /**
     * Exports the Annexure I file in requested format.
     *
     * @param financialYear Indian FY format (e.g. "2026-2027")
     * @param quarter quarter code ("Q1", "Q2", "Q3", or "Q4")
     * @param format "CSV" or "TXT"
     * @return binary file bytes
     */
    byte[] exportAnnexure1(String financialYear, String quarter, String format);
}
