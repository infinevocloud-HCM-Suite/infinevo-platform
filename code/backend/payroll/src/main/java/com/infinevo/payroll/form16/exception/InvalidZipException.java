package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded file is not a valid ZIP or is corrupted (W-36.5 §4).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidZipException extends RuntimeException {

    public InvalidZipException(String message) {
        super(message);
    }

    public InvalidZipException(String message, Throwable cause) {
        super(message, cause);
    }
}
