package com.infinevo.payroll.compliance.form24q;

/**
 * Service for Form 24Q Annexure II Annual Salary & Tax Statement (Q4) (W-89.3).
 */
public interface Form24QAnnexure2Service {

    /**
     * Previews the annual Annexure II employee tax computation breakdown for Q4.
     *
     * @param financialYear Indian FY format (e.g. "2026-2027")
     * @return preview summary and employee salary records
     */
    Annexure2PreviewResponse previewAnnexure2(String financialYear);

    /**
     * Exports the annual Annexure II statement in requested format.
     *
     * @param financialYear Indian FY format (e.g. "2026-2027")
     * @param format "CSV" or "TXT"
     * @return file bytes
     */
    byte[] exportAnnexure2(String financialYear, String format);
}
