package com.infinevo.payroll.salary;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for allocating a salary component within a CTC version (W-26.2).
 */
public record SalaryComponentItemRequest(
        UUID componentId,
        CalculationType calculationType,
        BigDecimal value,
        PercentageOf percentageOf,
        Boolean enabled,
        String earningFrequency,
        String carryForwardOption) {}
