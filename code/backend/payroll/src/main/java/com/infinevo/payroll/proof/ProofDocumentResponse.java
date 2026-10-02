package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** A file attached to a proof item: metadata only, the bytes come from the download endpoint (W-34.1). */
public record ProofDocumentResponse(
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size_bytes") long sizeBytes,
        @JsonProperty("uploaded_at") Instant uploadedAt) {}
