package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope (status, message, data) per CONVENTIONS.md §3 and spec §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, message, data);
    }

    public static <T> ApiResponse<T> of(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }
}
