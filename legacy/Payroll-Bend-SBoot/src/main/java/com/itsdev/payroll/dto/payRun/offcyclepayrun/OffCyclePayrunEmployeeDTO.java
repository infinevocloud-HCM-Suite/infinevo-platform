package com.itsdev.payroll.dto.payRun.offcyclepayrun;



import java.util.ArrayList;
import java.util.List;

public class OffCyclePayrunEmployeeDTO {

    private Long employeeId;

    private List<OffCyclePayrunEmployeeEarningDTO> earnings = new ArrayList<>();
    private List<OffCyclePayrunEmployeeDeductionDTO> deductions = new ArrayList<>();

    private List<Object> lopAdjustmentDetails = new ArrayList<>();
    private List<Object> taxes = new ArrayList<>();

    // Getters & Setters


    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public List<OffCyclePayrunEmployeeEarningDTO> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<OffCyclePayrunEmployeeEarningDTO> earnings) {
        this.earnings = earnings;
    }

    public List<OffCyclePayrunEmployeeDeductionDTO> getDeductions() {
        return deductions;
    }

    public void setDeductions(List<OffCyclePayrunEmployeeDeductionDTO> deductions) {
        this.deductions = deductions;
    }

    public List<Object> getLopAdjustmentDetails() {
        return lopAdjustmentDetails;
    }

    public void setLopAdjustmentDetails(List<Object> lopAdjustmentDetails) {
        this.lopAdjustmentDetails = lopAdjustmentDetails;
    }

    public List<Object> getTaxes() {
        return taxes;
    }

    public void setTaxes(List<Object> taxes) {
        this.taxes = taxes;
    }
}

