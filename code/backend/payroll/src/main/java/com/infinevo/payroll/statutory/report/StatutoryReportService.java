package com.infinevo.payroll.statutory.report;

import com.infinevo.core.report.ExportFormat;
import java.util.UUID;

/**
 * Service for generating statutory return reports (ESI and PT) from completed pay runs (W-95).
 */
public interface StatutoryReportService {

    /**
     * Generates raw monthly ESI Return bytes for a pay run in the requested format (XLSX or CSV).
     *
     * @param payrunId pay run identifier
     * @param format export format (XLSX or CSV)
     * @return report bytes
     */
    byte[] generateEsiReturn(UUID payrunId, ExportFormat format);

    /**
     * Generates raw state-wise Professional Tax Return bytes for a pay run in the requested format,
     * optionally filtered by state.
     *
     * @param payrunId pay run identifier
     * @param format export format (XLSX or CSV)
     * @param state optional state code filter (e.g. "KA")
     * @return report bytes
     */
    byte[] generatePtReturn(UUID payrunId, ExportFormat format, String state);

    /**
     * Generates monthly ESI Return file bundle (bytes, filename, and MediaType).
     *
     * @param payrunId pay run identifier
     * @param format export format (XLSX or CSV)
     * @return report file bundle
     */
    StatutoryReportFile generateEsiReturnFile(UUID payrunId, ExportFormat format);

    /**
     * Generates state-wise Professional Tax Return file bundle (bytes, filename, and MediaType).
     *
     * @param payrunId pay run identifier
     * @param format export format (XLSX or CSV)
     * @param state optional state code filter (e.g. "KA")
     * @return report file bundle
     */
    StatutoryReportFile generatePtReturnFile(UUID payrunId, ExportFormat format, String state);
}
