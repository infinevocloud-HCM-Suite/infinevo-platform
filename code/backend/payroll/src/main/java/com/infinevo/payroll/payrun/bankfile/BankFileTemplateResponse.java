package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.report.ExportFormat;
import java.time.Instant;
import java.util.List;

/**
 * A bank file template as the API shows it (W-88.2). {@code id} is a UUID, or {@code generic-neft} for the built-in.
 */
public record BankFileTemplateResponse(
        String id,
        String name,
        ExportFormat fileFormat,
        boolean includeHeader,
        List<BankFileColumn> columns,
        boolean builtIn,
        Instant updatedAt,
        String updatedBy) {

    static BankFileTemplateResponse from(BankFileTemplate template) {
        return new BankFileTemplateResponse(
                template.getId().toString(),
                template.getName(),
                template.getFileFormat(),
                template.isIncludeHeader(),
                List.copyOf(template.getColumns()),
                false,
                template.getUpdatedAt(),
                template.getUpdatedBy());
    }
}
