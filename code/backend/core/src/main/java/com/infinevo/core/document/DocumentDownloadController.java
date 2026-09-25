package com.infinevo.core.document;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/documents/download?t=<token>} — serves a stored file to whoever holds a signed,
 * unexpired link (W-21, decision D1).
 *
 * <p><strong>Unauthenticated, on purpose, and on the reviewed list.</strong> A link is opened from an
 * email or by a plain browser download, neither of which carries a bearer token. The path is in
 * {@link PublicEndpoints} — the {@code D-22} exception list — as one exact path, so security and the
 * tenant filter both let it through and nothing mapped beside or beneath it is opened by accident.
 *
 * <p><strong>The token is the authorisation.</strong> {@link DocumentLinkService#verify} checks the
 * signature, which covers the tenant, the document and the expiry together. This controller is then
 * the edge for its request: it binds the tenant the token names, reads under row-level security, and
 * clears the binding in {@code finally} — the job {@code TenantContextFilter} does for every other
 * request, done here because this request carries no identity for the filter to bind from.
 *
 * <p><strong>The app serves the bytes; Blob never does.</strong> The spec's first design had Blob
 * serve the file directly. W-51 closes that: the storage account has no public network path
 * ({@code infra/azure/modules/storage.bicep:34}) and SAS is forbidden
 * ({@code infra/azure/modules/rbac.bicep:18-20}). So the file travels storage → app → Front Door.
 *
 * <p>How this differs from the frozen payslip link it replaces
 * ({@code legacy/Payroll-Bend-SBoot/.../controller/payruns/PublicPayslipController.java}): one exact
 * path rather than the {@code /api/public/**} prefix that also published a controller minting links
 * for any payslip ({@code :69-99}); a link that expires; a token that is never logged ({@code :43-44}
 * logged it); and a file rather than a JSON body the browser had to render.
 *
 * <p><strong>Every refusal is the same {@code 404}</strong> — malformed, tampered, expired, deleted
 * or never existed — so the answer teaches nothing about which. Rate limiting, which {@code D-22} also
 * asks of a public endpoint, is the Front Door WAF's per-IP rule ({@code D-51}), not this class's.
 */
@RestController
public class DocumentDownloadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentDownloadController.class);

    static final String NOT_A_LINK = "This link is not valid or has expired";

    private final DocumentLinkService linkService;
    private final DocumentService documentService;

    public DocumentDownloadController(DocumentLinkService linkService, DocumentService documentService) {
        this.linkService = Objects.requireNonNull(linkService, "linkService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
    }

    @GetMapping(PublicEndpoints.DOCUMENT_DOWNLOAD)
    public ResponseEntity<?> download(@RequestParam(value = "t", required = false) String token) {
        Optional<DocumentLinkService.LinkClaims> claims = linkService.verify(token);
        if (claims.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, NOT_A_LINK);
        }

        UUID tenantId = claims.get().tenantId();
        UUID documentId = claims.get().documentId();
        TenantContext.set(tenantId);
        try {
            DocumentService.DocumentContent content = documentService.open(documentId);
            DocumentResponse metadata = content.metadata();
            log.info("Served document {} in tenant {} by signed link", documentId, tenantId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.contentType()))
                    .contentLength(metadata.sizeBytes())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(metadata.fileName(), StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    // The URL holds the token, so nothing may keep a copy of the response or pass the
                    // URL on as a referrer.
                    .cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(content.content()));
        } catch (DocumentService.NotFoundException e) {
            // Deleted since the link was issued, or never there: the same answer as a bad link.
            return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, NOT_A_LINK);
        } catch (DocumentService.StorageUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code, message, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
