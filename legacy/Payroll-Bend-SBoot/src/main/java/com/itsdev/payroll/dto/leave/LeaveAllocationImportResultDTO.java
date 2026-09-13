package com.itsdev.payroll.dto.leave;

import java.util.ArrayList;
import java.util.List;

public class LeaveAllocationImportResultDTO {
    private int totalRowsProcessed;
    private int successCount;
    private int failureCount;
    private List<ImportRowErrorDTO> errors = new ArrayList<>();

    public LeaveAllocationImportResultDTO() {}

    public LeaveAllocationImportResultDTO(int totalRowsProcessed, int successCount, int failureCount, List<ImportRowErrorDTO> errors) {
        this.totalRowsProcessed = totalRowsProcessed;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getTotalRowsProcessed() {
        return totalRowsProcessed;
    }

    public void setTotalRowsProcessed(int totalRowsProcessed) {
        this.totalRowsProcessed = totalRowsProcessed;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<ImportRowErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportRowErrorDTO> errors) {
        this.errors = errors;
    }
}
