package com.infinevo.hrms.performance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GoalCheckInRequest(
        @NotNull(message = "currentValue is required") BigDecimal currentValue,
        @Size(max = 2000, message = "notes must not exceed 2000 characters") String notes) {}

