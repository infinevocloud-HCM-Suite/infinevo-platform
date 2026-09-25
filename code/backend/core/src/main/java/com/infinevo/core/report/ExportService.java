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
