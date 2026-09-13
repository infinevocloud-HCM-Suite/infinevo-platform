package com.itsdev.payroll.dto.organization;

import jakarta.validation.constraints.NotBlank;

public class DepartmentDTO {

    private String departmentId;

    @NotBlank(message = "Department name is required")
    private String name;
    
    private String description;
    private String departmentCode;


    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
}
