package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to convert accepted job offer into an active core employee (W-112).
 */
public record ConvertToEmployeeRequest(
        @NotBlank(message = "employeeNumber is required") String employeeNumber, String workEmail, String gender) {}
