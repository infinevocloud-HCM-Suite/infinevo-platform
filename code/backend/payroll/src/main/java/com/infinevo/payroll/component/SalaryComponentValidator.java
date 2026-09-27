package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates salary component constraints per W-26.1 specification.
 */
public final class SalaryComponentValidator {

    private SalaryComponentValidator() {}

    public static void validateCommon(
            String code,
            String name,
            String displayName,
            CalculationType calculationType,
            BigDecimal defaultValue,
            PercentageOf percentageOf,
            BigDecimal maxLimit,
            boolean isCodeDuplicate,
            Map<String, String> fieldErrors) {

        if (code == null || code.isBlank()) {
            fieldErrors.put("code", "Code is required");
        } else if (code.length() > 32) {
            fieldErrors.put("code", "Code must not exceed 32 characters");
        } else if (isCodeDuplicate) {
            fieldErrors.put("code", "Code is already in use in this tenant");
        }

        if (name == null || name.isBlank()) {
            fieldErrors.put("name", "Name is required");
        } else if (name.length() > 128) {
            fieldErrors.put("name", "Name must not exceed 128 characters");
        }

        if (displayName != null && displayName.length() > 128) {
            fieldErrors.put("displayName", "Display name must not exceed 128 characters");
        }

        CalculationType calcType = calculationType != null ? calculationType : CalculationType.FLAT;

        if (defaultValue != null && defaultValue.compareTo(BigDecimal.ZERO) < 0) {
            fieldErrors.put("defaultValue", "Default value must not be negative");
        }

        if (maxLimit != null && maxLimit.compareTo(BigDecimal.ZERO) < 0) {
            fieldErrors.put("maxLimit", "Max limit must not be negative");
        }

        if (calcType == CalculationType.PERCENTAGE) {
            if (percentageOf == null) {
                fieldErrors.put("percentageOf", "percentage_of is required when calculation_type is PERCENTAGE");
            }
        } else if (calcType == CalculationType.FLAT) {
            if (percentageOf != null) {
                fieldErrors.put("percentageOf", "percentage_of must be null when calculation_type is FLAT");
            }
        }
    }

    public static void validateCommon(
            String code,
            String name,
            String displayName,
            CalculationType calculationType,
            BigDecimal defaultValue,
            PercentageOf percentageOf,
            BigDecimal maxLimit,
            boolean isCodeDuplicate) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateCommon(
                code,
                name,
                displayName,
                calculationType,
                defaultValue,
                percentageOf,
                maxLimit,
                isCodeDuplicate,
                errors);
        if (!errors.isEmpty()) {
            throw new ComponentValidationException(errors);
        }
    }
}
