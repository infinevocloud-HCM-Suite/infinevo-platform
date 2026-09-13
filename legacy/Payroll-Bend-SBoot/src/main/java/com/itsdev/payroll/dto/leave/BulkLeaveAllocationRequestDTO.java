package com.itsdev.payroll.dto.leave;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BulkLeaveAllocationRequestDTO {

    @NotBlank(message = "Year is required")
    private String year;

    private String leaveMonth;

    @NotEmpty(message = "At least one employee allocation is required")
    @Valid
    private List<EmployeeLeaveAllocationRequestDTO> allocations;

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public List<EmployeeLeaveAllocationRequestDTO> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<EmployeeLeaveAllocationRequestDTO> allocations) {
        this.allocations = allocations;
    }
}
