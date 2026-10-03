package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded ZIP file exceeds the 50 MB limit (W-36.5 §4).
 * Maps to HTTP 413 Payload Too Large.
 */
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class ZipTooLargeException extends RuntimeException {

    public ZipTooLargeException(String message) {
        super(message);
    }
}
