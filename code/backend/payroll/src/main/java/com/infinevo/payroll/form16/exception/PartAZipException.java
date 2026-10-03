package com.infinevo.payroll.form16.exception;

import java.io.Serial;

/**
 * The uploaded Part A file cannot be unpacked as asked (W-36.5 §4). Maps to {@code 400} with
 * {@link #code()} as the error code: {@code ZIP_INVALID}, {@code ZIP_ENCRYPTED} or {@code ZIP_LIMIT_EXCEEDED}.
 */
public class PartAZipException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String INVALID = "ZIP_INVALID";
    public static final String ENCRYPTED = "ZIP_ENCRYPTED";
    public static final String LIMIT_EXCEEDED = "ZIP_LIMIT_EXCEEDED";

    private final String code;

    public PartAZipException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PartAZipException invalid() {
        return new PartAZipException(INVALID, "The file is not a ZIP archive. Upload the Part A ZIP from TRACES.");
    }

    public static PartAZipException encrypted() {
        return new PartAZipException(
                ENCRYPTED, "The ZIP is password-protected. Extract it and re-zip it without a password.");
    }

    public static PartAZipException tooManyEntries(int limit) {
        return new PartAZipException(LIMIT_EXCEEDED, "The ZIP holds more than " + limit + " entries.");
    }

    public static PartAZipException tooLargeUnpacked(long limitBytes) {
        return new PartAZipException(
                LIMIT_EXCEEDED, "The ZIP unpacks to more than " + (limitBytes / (1024 * 1024)) + " MB.");
    }
}
