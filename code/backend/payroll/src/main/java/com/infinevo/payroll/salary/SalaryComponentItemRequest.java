package com.infinevo.payroll.salary;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for allocating a salary component within a CTC version (W-26.2).
 *
 * <p>{@code balancing} marks the one earning whose amount the server computes so the in-CTC total
 * equals the CTC exactly (W-75.1); {@code null} means {@code false}. Its sent {@code value} is ignored.
 */
public record SalaryComponentItemRequest(
        UUID componentId,
        CalculationType calculationType,
        BigDecimal value,
        PercentageOf percentageOf,
        Boolean enabled,
        String earningFrequency,
        String carryForwardOption,
        Boolean balancing) {

    public SalaryComponentItemRequest(
            UUID componentId,
            CalculationType calculationType,
            BigDecimal value,
            PercentageOf percentageOf,
            Boolean enabled,
            String earningFrequency,
            String carryForwardOption) {
        this(componentId, calculationType, value, percentageOf, enabled, earningFrequency, carryForwardOption, null);
    }
}
