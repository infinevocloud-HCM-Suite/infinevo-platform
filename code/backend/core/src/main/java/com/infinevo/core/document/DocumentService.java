package com.infinevo.core.document;

import java.io.InputStream;
import java.io.Serial;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * The document store (W-21, spec section 4) — and the seam every other area stores a file through
 * ({@code 12-core-contracts.md:107}).
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding filter
 * set from the verified token, or a job runner bound before calling. All logic lives in
 * {@link DocumentServiceImpl} — {@code docs/CONVENTIONS.md} section 3.
 */
public interface DocumentService {

    /**
     * Stores a file and returns its document id. The blob is written first, then the row.
     *
     * <p>Refuses a file over its kind's size limit ({@link TooLargeException} — 10 MB for anything a
     * client can upload, decision D2) and a file that is not one of the five accepted types — pdf,
     * jpg, png, xlsx, csv — by name <em>and</em> by content ({@link UnsupportedTypeException}).
     *
     * <p>The bytes are read into memory, bounded by the limit. For a file the platform generates and
     * that may be large — an export — use {@link #storeFile}, which streams.
     *
     * @param employeeId the employee the file belongs to, or {@code null} for a tenant-level file such
     *     as an export ({@code W-23.1}). When given, it must name a live employee in the bound tenant
     * @param fileName the original file name. Only its last path segment is kept, and its extension
     *     decides the type
     * @param content the bytes, read once and not closed here — the caller owns the stream
     */
    UUID store(DocumentKind kind, UUID employeeId, String fileName, InputStream content);

    /**
     * Stores a file already on disk, streamed to storage rather than read into memory — for output
     * the platform generates, such as an export ({@code W-23.1}) or a payslip ({@code W-36}). The same
     * rules as {@link #store}; the file is not deleted here, the caller owns it.
     */
    UUID storeFile(DocumentKind kind, UUID employeeId, String fileName, Path file);

    /** The metadata of a live document in the bound tenant. Never the bytes. */
    DocumentResponse get(UUID id);

    /**
     * The metadata and the bytes of a live document in the bound tenant — for the one endpoint that
     * serves a signed link ({@code DocumentDownloadController}). The caller closes the stream.
     */
    DocumentContent open(UUID id);

    /** A document and an open stream of its bytes. */
    record DocumentContent(DocumentResponse metadata, InputStream content) {}

    /** Soft-deletes a document in the bound tenant. The row stays hidden and the blob is kept. */
    void delete(UUID id);

    /** No such live document in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No document " + id + " in this tenant");
        }
    }

    /** The request cannot be applied. Maps to {@code 400} with per-field detail. */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldErrors;

        public ValidationException(Map<String, String> fieldErrors) {
            super("The request was not valid: " + fieldErrors);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }

    /** Over the upload limit. Maps to {@code 413}. */
    class TooLargeException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public TooLargeException(long maxBytes) {
            super("The file is larger than the " + maxBytes + "-byte limit");
        }
    }

    /** Not one of the accepted types, by name or by content. Maps to {@code 415}. */
    class UnsupportedTypeException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public UnsupportedTypeException(String message) {
            super(message);
        }
    }

    /** There is no storage to write to or read from in this runtime. Maps to {@code 503}. */
    class StorageUnavailableException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public StorageUnavailableException(String message) {
            super(message);
        }
    }
}
