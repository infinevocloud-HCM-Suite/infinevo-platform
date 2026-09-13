package com.itsdev.payroll.dto.leave;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class EmployeeLeaveConsumptionRequestDTO {

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    private String leaveMonth;

    private Integer lwp;

    private Integer lopDays;

    @NotEmpty(message = "At least one leave type consumption record is required")
    @Valid
    private List<LeaveAllocationItemDTO> leaveTypes;

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public Integer getLwp() {
        return lwp;
    }

    public void setLwp(Integer lwp) {
        this.lwp = lwp;
    }

    public Integer getLopDays() {
        return lopDays;
    }

    public void setLopDays(Integer lopDays) {
        this.lopDays = lopDays;
    }

    public List<LeaveAllocationItemDTO> getLeaveTypes() {
        return leaveTypes;
    }

    public void setLeaveTypes(List<LeaveAllocationItemDTO> leaveTypes) {
        this.leaveTypes = leaveTypes;
    }
}