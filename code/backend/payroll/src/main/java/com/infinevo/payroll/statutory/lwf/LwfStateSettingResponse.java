package com.infinevo.payroll.statutory.lwf;

import java.math.BigDecimal;
import java.util.List;

/**
 * LWF setting response for a state in an organization (W-92).
 */
public record LwfStateSettingResponse(
        String stateCode,
        String stateName,
        boolean leviesLwf,
        boolean isEnabled,
        String deductionCycle,
        List<Integer> deductionMonths,
        String calcMode,
        BigDecimal employeeContribution,
        BigDecimal employerContribution,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal employeeCap,
        BigDecimal employerCap,
        String registrationNumber,
        int activeLocationsCount) {}
