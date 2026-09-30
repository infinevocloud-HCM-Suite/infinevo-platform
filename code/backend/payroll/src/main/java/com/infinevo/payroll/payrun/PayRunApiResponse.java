package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code status} / {@code message} / {@code data} envelope (CONVENTIONS.md §3, DEBT-008). */
public record PayRunApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> PayRunApiResponse<T> ok(String message, T data) {
        return new PayRunApiResponse<>(200, message, data);
    }

    public static <T> PayRunApiResponse<T> created(String message, T data) {
        return new PayRunApiResponse<>(201, message, data);
    }
}
