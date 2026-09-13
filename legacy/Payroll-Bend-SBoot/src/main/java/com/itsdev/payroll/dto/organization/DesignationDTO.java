package com.itsdev.payroll.dto.organization;

import jakarta.validation.constraints.NotBlank;

public class DesignationDTO {

    private String designationId;
    
    @NotBlank(message = "Designation name is required")
    private String name;


    public String getDesignationId() { return designationId; }
    public void setDesignationId(String designationId) { this.designationId = designationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
