package com.infinevo.core.org;

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
 * The error contract the three org-master controllers share (W-14.1).
 *
 * <p>The handlers are local to this hierarchy rather than a {@code @RestControllerAdvice}, for the
 * reason {@code EmployeeController} gives: there is no global advice in the platform yet, and
 * inventing one here would quietly set the error contract for every future controller from inside a
 * feature branch. They return the shared {@link ApiErrorResponse} envelope, so the shape is already
 * the common one when that advice arrives.
 *
 * <p>Three controllers extending one base rather than three copies of the same four handlers: an
 * error shape that is copied three times is an error shape that will diverge twice.
 */
abstract class OrgMasterController {

    @ExceptionHandler(OrgMasterService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OrgMasterService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(OrgMasterService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(OrgMasterService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    /**
     * {@code 409}, never a constraint-violation stack trace. The message names the code and says "in
     * this tenant", because the same code in another tenant is legal.
     */
    @ExceptionHandler(OrgMasterService.DuplicateCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCode(OrgMasterService.DuplicateCodeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /**
     * {@code 409} on a delete that employees still depend on — spec section 4. The message names
     * deactivation, which is the supported way to retire a value that is in use.
     */
    @ExceptionHandler(OrgMasterService.RecordInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleInUse(OrgMasterService.RecordInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /**
     * A body Jackson could not read at all — malformed JSON, or a string where a boolean belongs.
     *
     * <p>Without this the request still fails safely with a {@code 400} and nothing reaches the
     * database, but it comes back in Spring's default body while every other error here uses
     * {@link ApiErrorResponse}. One endpoint answering in two shapes is the kind of thing a client
     * writes a special case for and never removes.
     *
     * <p>The exception's own message is not echoed: it carries the offending JSON and the internal
     * type names, and neither belongs in a response.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
