package com.infinevo.payroll.deduction;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code status} / {@code message} / {@code data} envelope (W-35.2 §4, {@code CONVENTIONS.md} §3). */
public record DeductionApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> DeductionApiResponse<T> ok(String message, T data) {
        return new DeductionApiResponse<>(200, message, data);
    }

    public static <T> DeductionApiResponse<T> created(String message, T data) {
        return new DeductionApiResponse<>(201, message, data);
    }
}
