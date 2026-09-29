package com.infinevo.payroll.reimbursement;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard status / message / data envelope for reimbursement claim API responses (W-35.1, CONVENTIONS.md §3).
 */
public record ReimbursementApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> ReimbursementApiResponse<T> ok(String message, T data) {
        return new ReimbursementApiResponse<>(200, message, data);
    }

    public static <T> ReimbursementApiResponse<T> created(String message, T data) {
        return new ReimbursementApiResponse<>(201, message, data);
    }
}
