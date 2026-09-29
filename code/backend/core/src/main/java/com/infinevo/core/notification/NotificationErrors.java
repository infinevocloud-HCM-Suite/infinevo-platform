package com.infinevo.core.notification;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The error contract the two notification controllers share (W-20.1) — local handlers in the shared
 * envelope, for the reason {@code OrgMasterController} gives.
 */
abstract class NotificationErrors {

    @ExceptionHandler(NotificationService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotificationService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NotificationService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(NotificationService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    /** An event or channel outside the closed lists, or a body that is not JSON. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleUnreadable(Exception e) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiError.VALIDATION_FAILED,
                "The request could not be read. Check the event name, that channel is IN_APP or EMAIL, and that"
                        + " the JSON is well formed.");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
