package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** One active certificate in the officer list (W-36.5 §3). */
public record PartARow(
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("source_file_name") String sourceFileName,
        @JsonProperty("uploaded_at") Instant uploadedAt) {

    static PartARow from(Form16PartA row) {
        return new PartARow(row.getEmployeeId(), row.getDocumentId(), row.getSourceFileName(), row.getCreatedAt());
    }
}
