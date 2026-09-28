package com.infinevo.payroll.salary;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response payload for an allocated salary component line in a CTC version (W-26.2).
 */
public record SalaryComponentItemResponse(
        UUID id,
        UUID componentId,
        String componentCode,
        String componentName,
        CalculationType calculationType,
        BigDecimal value,
        PercentageOf percentageOf,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount,
        boolean enabled,
        boolean includedInCtc,
        String earningFrequency,
        String carryForwardOption) {}
