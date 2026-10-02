package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.portal.MyTimesheetController;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps the timesheet exceptions to the standard error body (W-42.1), for the two timesheet controllers and no others.
 *
 * <p>A plain {@code ControllerAdvice}, not the rest variant: every handler returns a {@code ResponseEntity}, so the
 * body is written as JSON either way, and this class is not a controller (a source check counts every class whose
 * text names the rest annotation as one).
 *
 * <p>Not found is {@code 404} (and is also what someone else's timesheet answers), a broken rule is {@code 400}, a
 * state conflict is {@code 409}. The permission error is not handled here: the shared handler answers it {@code 403}.
 */
@ControllerAdvice(assignableTypes = {TimesheetController.class, MyTimesheetController.class})
class TimesheetErrors {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiErrorResponse> validation(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(TimesheetConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(TimesheetConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> notReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed, dates are YYYY-MM-DD"
                                + " and hours are numbers.",
                        traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        String name = e.getName() != null ? e.getName() : "parameter";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(
                        Map.of(name, "Invalid value for parameter: " + e.getValue()), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
