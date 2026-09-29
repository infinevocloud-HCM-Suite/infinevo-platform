package com.infinevo.core.document;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * {@code /api/v1/documents} — upload and soft delete (W-21, spec section 4). The two reads, metadata
 * and signed link, are {@link DocumentReadController}'s: their guard is two codes, not one.
 *
 * <p>Thin by rule: unpack, delegate, repack — {@code docs/CONVENTIONS.md} section 3. No endpoint names
 * a tenant; {@code TenantContextFilter} bound it from the verified token.
 *
 * <p><strong>The bytes never come back through here.</strong> The metadata read returns no content
 * and {@code /link} returns a signed, expiring URL, both in {@link DocumentReadController}. That URL
 * is served by {@link DocumentDownloadController}, a separate class because it takes no bearer token —
 * the link is opened from an email or a plain browser download — and sits on the {@code D-22}
 * exception list, where every method here requires an action.
 *
 * <p>The exception handlers are local, for the reason {@code EmployeeController} gives: there is no
 * global advice yet, and a feature branch should not quietly set the error contract for every future
 * controller. They return the shared {@link ApiErrorResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
    }

    /**
     * {@code 201} with the stored document's metadata; {@code 413} over the size limit; {@code 415} for
     * a type outside the five.
     *
     * <p>{@code PAYSLIP} and {@code EXPORT} are refused {@code 400}: the platform writes those itself
     * ({@link DocumentKind#isUploadable}), and accepting one from a client would let a caller file a
     * document of their own making as a payslip.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAction("core.document.upload")
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("kind") DocumentKind kind,
            @RequestParam(value = "employeeId", required = false) UUID employeeId)
            throws IOException {
        if (!kind.isUploadable()) {
            throw new DocumentService.ValidationException(
                    Map.of("kind", kind + " is written by the platform and cannot be uploaded"));
        }
        UUID id;
        try (InputStream content = file.getInputStream()) {
            id = documentService.store(kind, employeeId, file.getOriginalFilename(), content);
        }
        return ResponseEntity.created(URI.create("/api/v1/documents/" + id)).body(documentService.get(id));
    }

    /** Soft delete — {@code 204}, and the row and the blob both stay. */
    @DeleteMapping("/{id}")
    @RequiresAction("core.document.delete")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code 404} — no such document in the bound tenant. The same answer whether it never existed,
     * was deleted, or belongs to another tenant, so the answer cannot be used to enumerate ids.
     */
    @ExceptionHandler(DocumentService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DocumentService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DocumentService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(DocumentService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(DocumentService.TooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(DocumentService.TooLargeException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    /**
     * Spring's own multipart limit, which sits just above the store's ({@code app/application.yml}), so
     * a file between the two gets the store's sentence and one far over it gets this one — the same
     * {@code 413} either way.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, "The file is too large");
    }

    @ExceptionHandler(DocumentService.UnsupportedTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedType(DocumentService.UnsupportedTypeException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    /** {@code 503} — this runtime has no document storage configured. The message says so plainly. */
    @ExceptionHandler(DocumentService.StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(DocumentService.StorageUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
    }

    /**
     * A request the framework could not bind: no {@code file} part, no {@code kind}, a {@code kind}
     * outside {@link DocumentKind}, an id that is not a UUID, or a body that is not multipart at all.
     *
     * <p>The exception's own message is not echoed: it carries the internal type names.
     */
    @ExceptionHandler({
        MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        MultipartException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(Exception e) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiError.VALIDATION_FAILED,
                "The request could not be read. Send multipart/form-data with a 'file' part and a 'kind' of"
                        + " EMPLOYEE_DOCUMENT, LEAVE_ATTACHMENT, REIMBURSEMENT_RECEIPT or INVESTMENT_PROOF.");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
