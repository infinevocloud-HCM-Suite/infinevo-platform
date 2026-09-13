package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.time.LocalDateTime;

public class POIApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String requestId;
    
    // Constructors
    public POIApiResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public POIApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    
    // Factory methods for common responses
    public static <T> POIApiResponse<T> success(T data) {
        return new POIApiResponse<>(true, "Operation successful", data);
    }
    
    public static <T> POIApiResponse<T> success(String message, T data) {
        return new POIApiResponse<>(true, message, data);
    }
    
    public static <T> POIApiResponse<T> error(String message) {
        return new POIApiResponse<>(false, message, null);
    }
    
    public static <T> POIApiResponse<T> error(String message, T data) {
        return new POIApiResponse<>(false, message, data);
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}