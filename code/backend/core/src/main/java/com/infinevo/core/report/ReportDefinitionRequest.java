package com.infinevo.core.report;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/v1/report-definitions} and {@code PUT /api/v1/report-definitions/{id}} (W-23.1).
 *
 * <p>{@code code} is read on create only and fixed afterwards, as a role's is. No tenant field — the
 * tenant is {@code TenantContext}'s.
 *
 * @param source a registered {@link ReportSource#code()}
 * @param columns column names from that source's allow-list, in the order the file should show them
 * @param defaultFilters filters the source declares, applied unless the export request overrides them
 * @param requiredAction the action a caller must hold to run this definition, on top of
 *     {@code core.report.read}
 */
public record ReportDefinitionRequest(
        String code,
        String name,
        String source,
        List<String> columns,
        Map<String, String> defaultFilters,
        ExportFormat format,
        String requiredAction) {}
