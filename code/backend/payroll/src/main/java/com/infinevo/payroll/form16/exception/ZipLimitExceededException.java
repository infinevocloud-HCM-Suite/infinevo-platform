package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded ZIP archive exceeds entry limits (2,000 entries or 200 MB unpacked) (W-36.5 §3, §4).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ZipLimitExceededException extends RuntimeException {

    public ZipLimitExceededException(String message) {
        super(message);
    }
}
