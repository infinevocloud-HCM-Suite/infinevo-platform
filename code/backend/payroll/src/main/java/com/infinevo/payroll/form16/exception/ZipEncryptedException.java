package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded Form 16 Part A ZIP is password-protected / encrypted (W-36.5 §2, §4).
 * Maps to HTTP 400 Bad Request with reason ZIP_ENCRYPTED.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "ZIP_ENCRYPTED")
public class ZipEncryptedException extends RuntimeException {

    public ZipEncryptedException() {
        super("extract and re-zip without a password");
    }

    public ZipEncryptedException(String message) {
        super(message);
    }
}
