package com.infinevo.core.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One report definition as the API returns it (W-23.1). */
public record ReportDefinitionResponse(
        UUID id,
        String code,
        String name,
        String source,
        List<String> columns,
        Map<String, String> defaultFilters,
        ExportFormat format,
        String requiredAction,
        boolean system,
        Instant createdAt,
        Instant updatedAt) {

    public static ReportDefinitionResponse from(ReportDefinition definition) {
        return new ReportDefinitionResponse(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getSource(),
                definition.getColumns(),
                definition.getDefaultFilters(),
                definition.getFormat(),
                definition.getRequiredAction(),
                definition.isSystem(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }
}
