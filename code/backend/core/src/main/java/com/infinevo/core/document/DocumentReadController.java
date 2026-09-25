package com.infinevo.core.document;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * {@code GET /api/v1/documents/{id}} and {@code /{id}/link} — the two reads (W-21, spec section 4).
 *
 * <p>Apart from {@link DocumentController} because their guard is "{@code core.document.read}, or
 * {@code core.document.read_own} when the document is the caller's", which {@code @RequiresAction}
 * cannot say with one code. {@link DocumentReadAccess} decides it and fails closed, and this class sits
 * on {@code EndpointGuardCoverageTest}'s exempt list with that reason. When {@code W-13.4} adds
 * {@code anyOf} to the annotation, both methods can carry
 * {@code @RequiresAction(value = "core.document.read", anyOf = "core.document.read_own")} as well.
 *
 * <p>A refusal is the shared {@code 403} envelope ({@code AuthzExceptionHandler}), the same answer
 * {@code @RequiresAction} gives.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentReadController {

    private final DocumentReadAccess access;
    private final DocumentLinkService linkService;

    DocumentReadController(DocumentReadAccess access, DocumentLinkService linkService) {
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.linkService = Objects.requireNonNull(linkService, "linkService must not be null");
    }

    /** Metadata, never the bytes. */
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable("id") UUID id) {
        return access.readable(id);
    }

    /** A {@link DocumentLinkService#INTERACTIVE_TTL fifteen-minute} link, for whoever may read the document. */
    @GetMapping("/{id}/link")
    public DocumentLinkService.SignedLink link(@PathVariable("id") UUID id) {
        access.readable(id);
        return linkService.signedLink(id);
    }

    /** {@code 404}, the same answer whether the document never existed, was deleted, or is another tenant's. */
    @ExceptionHandler(DocumentService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DocumentService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    /** An id that is not a UUID. The exception's own message is not echoed: it names internal types. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleBadId(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "The document id must be a UUID");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId == null || traceId.isBlank()
                                ? UUID.randomUUID().toString().substring(0, 8)
                                : traceId));
    }
}
