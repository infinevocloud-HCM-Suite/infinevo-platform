package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard API response envelope (CONVENTIONS.md §3).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @JsonProperty("status") String status, @JsonProperty("message") String message, @JsonProperty("data") T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", "Operation successful", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}
