package com.infinevo.payroll.proof;

import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * The error contract and the file response the two proof controllers share (W-34.1).
 *
 * <p>The handlers are inherited rather than global, for the reason {@code DocumentController} gives:
 * there is no global advice, and a feature should not set the error contract for every controller.
 * Messages are ours, never an exception's own text, which can carry internal type names.
 */
abstract class ProofControllerSupport {

    @ExceptionHandler(ProofConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ProofConflictException e) {
        Map<String, String> fields = e.itemIds().isEmpty()
                ? Map.of()
                : Map.of("item_ids", e.itemIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(e.reasonCode(), e.getMessage(), fields, traceId(), Instant.now()));
    }

    @ExceptionHandler(ProofNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ProofNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProofValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ProofValidationException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, PermissionDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(Exception e) {
        return error(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN, e.getMessage());
    }

    /** A financial year that is not {@code YYYY-YYYY}. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(DocumentService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentNotFound(DocumentService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, "No such file");
    }

    @ExceptionHandler(DocumentService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentValidation(DocumentService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(DocumentService.TooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(DocumentService.TooLargeException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, "The file is too large");
    }

    @ExceptionHandler(DocumentService.UnsupportedTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedType(DocumentService.UnsupportedTypeException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(DocumentService.StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageUnavailable(DocumentService.StorageUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
    }

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
                "The request could not be read. Check the ids and send a JSON body, or multipart/form-data with a 'file' part.");
    }

    /**
     * A file to the browser. {@code no-store} and {@code nosniff}: the file is an employee's own
     * document, and it must not be cached or reinterpreted as another type.
     */
    static ResponseEntity<InputStreamResource> file(DocumentService.DocumentContent content) {
        var metadata = content.metadata();
        MediaType type;
        try {
            type = MediaType.parseMediaType(metadata.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(metadata.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(metadata.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.content()));
    }

    static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
