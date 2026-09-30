package com.infinevo.payroll.taxdeductor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard status / message / data envelope for tax deductor API responses (W-36.3, CONVENTIONS.md §3).
 */
public record TaxDeductorApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> TaxDeductorApiResponse<T> ok(String message, T data) {
        return new TaxDeductorApiResponse<>(200, message, data);
    }
}
