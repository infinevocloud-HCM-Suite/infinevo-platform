package com.infinevo.core.report;

import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * The error contract {@link ReportDefinitionController} and {@link ExportController} share (W-23.1).
 *
 * <p>Local handlers returning the shared {@link ApiErrorResponse} envelope, for the reason
 * {@code OrgMasterController} gives: there is no global advice yet, and a feature branch should not
 * quietly set the error contract for every future controller. A missing action is not handled here —
 * {@code AuthzExceptionHandler} answers it {@code 403} for every controller alike.
 */
abstract class ReportController {

    @ExceptionHandler(ReportDefinitionService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ReportDefinitionService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ReportDefinitionService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ReportDefinitionService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler({
        ReportDefinitionService.DuplicateCodeException.class,
        ReportDefinitionService.SystemDefinitionException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    /** The export was written but is over the system file limit (decision D2). */
    @ExceptionHandler(DocumentService.TooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(DocumentService.TooLargeException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    /** {@code 503} — no document storage in this runtime, so an export has nowhere to go. */
    @ExceptionHandler(DocumentService.StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(DocumentService.StorageUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiError.VALIDATION_FAILED,
                "The request body could not be read. Check the JSON is well formed and that format is CSV or XLSX.");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
