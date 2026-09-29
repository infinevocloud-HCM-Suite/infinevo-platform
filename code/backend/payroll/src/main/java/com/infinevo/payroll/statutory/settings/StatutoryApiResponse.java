package com.infinevo.payroll.statutory.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard status / message / data envelope for statutory settings API responses (W-31.1).
 */
public record StatutoryApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> StatutoryApiResponse<T> ok(String message, T data) {
        return new StatutoryApiResponse<>(200, message, data);
    }
}
