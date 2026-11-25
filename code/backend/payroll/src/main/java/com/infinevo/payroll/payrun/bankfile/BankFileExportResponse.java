package com.infinevo.payroll.payrun.bankfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A bank file made for a pay run, as the history lists it (W-88.2). */
public record BankFileExportResponse(
        UUID id,
        UUID payrunId,
        UUID templateId,
        String templateName,
        String fileName,
        int rowCount,
        BigDecimal fileTotal,
        Integer selectedEmployeeCount,
        Instant createdAt,
        String createdBy) {

    static BankFileExportResponse from(BankFileExport export) {
        return new BankFileExportResponse(
                export.getId(),
                export.getPayrunId(),
                export.getTemplateId(),
                export.getTemplateName(),
                export.getFileName(),
                export.getRowCount(),
                export.getFileTotal(),
                export.getSelectedEmployeeCount(),
                export.getCreatedAt(),
                export.getCreatedBy());
    }
}
