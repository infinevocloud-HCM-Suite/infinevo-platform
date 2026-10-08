package com.infinevo.core.document;

import java.time.Instant;
import java.util.UUID;

/**
 * One document's metadata as the API returns it (W-21, spec section 4). Never the bytes.
 *
 * <p>The container and path are absent: they are storage internals, and the only way to the bytes
 * is a signed link from {@code GET /api/v1/documents/{id}/link}. {@code isDeleted} is absent because
 * a deleted document is never returned at all. {@code label} is what an employee document is
 * (W-73.5) — null for every other kind.
 */
public record DocumentResponse(
        UUID id,
        UUID employeeId,
        DocumentKind kind,
        DocumentLabel label,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Instant createdAt,
        String createdBy) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getEmployeeId(),
                document.getKind(),
                document.getLabel(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getChecksumSha256(),
                document.getCreatedAt(),
                document.getCreatedBy());
    }
}
