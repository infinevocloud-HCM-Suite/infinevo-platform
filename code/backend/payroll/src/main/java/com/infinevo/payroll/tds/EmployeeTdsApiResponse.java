package com.infinevo.payroll.tds;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API envelope (status, message, data) per CONVENTIONS.md §3 and W-36.1 §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeTdsApiResponse<T>(int status, String message, T data) {

    public static <T> EmployeeTdsApiResponse<T> ok(String message, T data) {
        return new EmployeeTdsApiResponse<>(200, message, data);
    }

    public static <T> EmployeeTdsApiResponse<T> created(String message, T data) {
        return new EmployeeTdsApiResponse<>(201, message, data);
    }
}
