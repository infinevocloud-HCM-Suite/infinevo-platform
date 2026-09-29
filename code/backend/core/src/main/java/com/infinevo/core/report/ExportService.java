package com.infinevo.core.report;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One export path for every screen (W-23.1, spec section 3) — {@code 09-build-order.md:207}: "three
 * screens use one export path".
 */
public interface ExportService {

    /**
     * Runs a definition under the bound tenant and returns a link to the file.
     *
     * <p>The caller must hold the definition's {@code required_action}, checked here, on top of the
     * {@code core.report.read} the endpoint requires. The file goes to the document store as an
     * {@code EXPORT} with no employee (W-21), so a large export does not hold a request thread
     * streaming bytes and the link can be fetched again.
     *
     * @throws ReportDefinitionService.NotFoundException no such definition in the bound tenant
     * @throws com.infinevo.shared.authz.PermissionDeniedException the caller lacks its required action
     */
    ExportResponse export(UUID definitionId, Map<String, String> filters);

    /**
     * The same export, for a background job — a schedule's run or an async export (W-23.2) — whose
     * caller was checked when the schedule was saved or the job was queued.
     *
     * <p>There is no caller to check again: the worker runs with nobody signed in, and
     * {@code PermissionService} refuses when there is nobody, so {@link #export} would fail every
     * job. Everything else — the tenant, the definition, the source's columns and filters, the
     * streaming, the document store — is identical. Never call this from a request path.
     */
    ExportResponse exportForJob(UUID definitionId, Map<String, String> filters);

    /**
     * {@code 503} — this runtime cannot run an export in the background: no queue producer is
     * configured, or the queue refused the message. Nothing is left waiting on it.
     */
    class BackgroundExportUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BackgroundExportUnavailableException(String message) {
            super(message);
        }
    }

    /** {@code POST /api/v1/exports}. {@code filters} override the definition's defaults. */
    record ExportRequest(UUID definitionId, Map<String, String> filters) {}

    /** The stored export and a fifteen-minute link to it. */
    record ExportResponse(
            UUID documentId, String fileName, ExportFormat format, long rowCount, String url, Instant expiresAt) {

        /** Withholds the URL, which carries the link's signature — see {@code DocumentLinkService.SignedLink}. */
        @Override
        public String toString() {
            return "ExportResponse[documentId=" + documentId + ", fileName=" + fileName + ", format=" + format
                    + ", rowCount=" + rowCount + ", url=<withheld>, expiresAt=" + expiresAt + "]";
        }
    }
}
