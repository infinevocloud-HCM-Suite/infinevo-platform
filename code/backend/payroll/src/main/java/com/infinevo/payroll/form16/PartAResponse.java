package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope for Form 16 Part A endpoints matching CONVENTIONS.md §3 and W-36.5 §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartAResponse<T>(int status, String message, T data) {

    public static <T> PartAResponse<T> ok(String message, T data) {
        return new PartAResponse<>(200, message, data);
    }
}
