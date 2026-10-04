package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code status} / {@code message} / {@code data} envelope (CONVENTIONS.md §3) for W-36.5. */
public record PartAResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> PartAResponse<T> ok(String message, T data) {
        return new PartAResponse<>(200, message, data);
    }
}
