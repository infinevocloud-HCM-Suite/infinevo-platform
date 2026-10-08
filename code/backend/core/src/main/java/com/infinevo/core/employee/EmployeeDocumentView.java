package com.infinevo.core.employee;

import com.infinevo.core.document.DocumentLabel;
import com.infinevo.core.document.DocumentResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the employee page's Documents tab (W-73.5, spec section 4) — what the table shows and
 * nothing more: no content type, no checksum, never a storage path. The bytes are reached through
 * {@code GET /api/v1/documents/{id}/link}, as everywhere else.
 *
 * @param uploadedBy the uploader's name from {@code core.user_account}, or the stored {@code created_by}
 *     when no account matches it
 * @param uploadedAt the document's {@code created_at}
 */
public record EmployeeDocumentView(
        UUID id, String fileName, DocumentLabel label, long sizeBytes, String uploadedBy, Instant uploadedAt) {

    /** The row for {@code document}, its uploader shown as {@code uploadedBy}. */
    public static EmployeeDocumentView from(DocumentResponse document, String uploadedBy) {
        return new EmployeeDocumentView(
                document.id(),
                document.fileName(),
                document.label(),
                document.sizeBytes(),
                uploadedBy,
                document.createdAt());
    }
}
