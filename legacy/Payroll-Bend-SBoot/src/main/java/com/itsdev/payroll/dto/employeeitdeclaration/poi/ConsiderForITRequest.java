package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import jakarta.validation.constraints.NotBlank;

public class ConsiderForITRequest {

    @NotBlank(message = "Confirmed by is required")
    private String confirmedBy;   // Admin username / admin employee code

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }
}
