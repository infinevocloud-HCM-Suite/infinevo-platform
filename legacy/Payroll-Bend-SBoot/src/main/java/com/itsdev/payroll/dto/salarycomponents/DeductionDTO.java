package com.itsdev.payroll.dto.salarycomponents;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;

public class DeductionDTO {

    private String deductionId;
    
    @NotBlank(message = "Deduction name is required")
    private String deductionName;
    
    private String deductionType;
    private String deductionTypeFormatted;
    private String status = "active";
    private String statusFormatted;
    private Boolean isRecurring;
    private LocalDateTime createdTime;
    private Boolean isUserConfigurable;
    private Boolean isAssociatedWithEmployee;
    private String perquisiteInterestRate;
    private String emiInterestRate;
    private String emiType;
    private Boolean isDeleted = false;


    public String getDeductionId() {
        return deductionId;
    }

    public void setDeductionId(String deductionId) {
        this.deductionId = deductionId;
    }

    public String getDeductionName() {
        return deductionName;
    }

    public void setDeductionName(String deductionName) {
        this.deductionName = deductionName;
    }

    public String getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(String deductionType) {
        this.deductionType = deductionType;
    }

    public String getDeductionTypeFormatted() {
        return deductionTypeFormatted;
    }

    public void setDeductionTypeFormatted(String deductionTypeFormatted) {
        this.deductionTypeFormatted = deductionTypeFormatted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFormatted() {
        return statusFormatted;
    }

    public void setStatusFormatted(String statusFormatted) {
        this.statusFormatted = statusFormatted;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    public void setIsRecurring(Boolean isRecurring) {
        this.isRecurring = isRecurring;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Boolean getIsUserConfigurable() {
        return isUserConfigurable;
    }

    public void setIsUserConfigurable(Boolean isUserConfigurable) {
        this.isUserConfigurable = isUserConfigurable;
    }

    public Boolean getIsAssociatedWithEmployee() {
        return isAssociatedWithEmployee;
    }

    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) {
        this.isAssociatedWithEmployee = isAssociatedWithEmployee;
    }

    public String getPerquisiteInterestRate() {
        return perquisiteInterestRate;
    }

    public void setPerquisiteInterestRate(String perquisiteInterestRate) {
        this.perquisiteInterestRate = perquisiteInterestRate;
    }

    public String getEmiInterestRate() {
        return emiInterestRate;
    }

    public void setEmiInterestRate(String emiInterestRate) {
        this.emiInterestRate = emiInterestRate;
    }

    public String getEmiType() {
        return emiType;
    }

    public void setEmiType(String emiType) {
        this.emiType = emiType;
    }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }


}
