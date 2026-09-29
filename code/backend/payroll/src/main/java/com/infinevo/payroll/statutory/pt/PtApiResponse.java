package com.infinevo.payroll.statutory.pt;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard status / message / data envelope for professional tax API responses (W-31.2, spec section 4).
 */
public record PtApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> PtApiResponse<T> ok(String message, T data) {
        return new PtApiResponse<>(200, message, data);
    }
}
