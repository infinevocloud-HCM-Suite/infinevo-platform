package com.infinevo.payroll.payslip;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard status / message / data envelope (CONVENTIONS.md §3, DEBT-008).
 */
public record PayslipApiResponse<T>(
        @JsonProperty("status") int status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> PayslipApiResponse<T> ok(String message, T data) {
        return new PayslipApiResponse<>(200, message, data);
    }
}
