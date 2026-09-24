package com.infinevo.core.authz;

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
 * The error contract {@link ActionController}, {@link RoleController} and {@link UserRoleController}
 * share (W-11.1).
 *
 * <p>Local handlers returning the shared {@link ApiErrorResponse} envelope, for the reason
 * {@code OrgMasterController} gives: there is no global advice yet, and a feature branch should not
 * quietly set the error contract for every future controller.
 */
abstract class AuthzController {

    @ExceptionHandler(RoleService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(RoleService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    /** {@code 400}, with the unknown action codes named in the {@code actionCodes} field error. */
    @ExceptionHandler(RoleService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(RoleService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(RoleService.DuplicateCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(RoleService.DuplicateCodeException e) {
        return conflict(e);
    }

    @ExceptionHandler(RoleService.SystemRoleException.class)
    public ResponseEntity<ApiErrorResponse> handleSystemRole(RoleService.SystemRoleException e) {
        return conflict(e);
    }

    @ExceptionHandler(RoleService.RoleInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleInUse(RoleService.RoleInUseException e) {
        return conflict(e);
    }

    /** Malformed JSON — the same envelope as every other error, without echoing the body back. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static ResponseEntity<ApiErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
