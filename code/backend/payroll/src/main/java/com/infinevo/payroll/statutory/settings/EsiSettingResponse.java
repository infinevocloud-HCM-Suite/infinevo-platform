package com.infinevo.payroll.statutory.settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for ESI settings (W-31.1).
 */
public record EsiSettingResponse(
        UUID id,
        UUID tenantId,
        boolean isEnabled,
        String registrationNumber,
        LocalDate registrationDate,
        DeductionCycle deductionCycle,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal wageCeiling,
        boolean includeEmployerInCtc,
        boolean includeInStructure,
        SettingSource source) {

    public static EsiSettingResponse from(EsiSetting entity) {
        return new EsiSettingResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.isEnabled(),
                entity.getRegistrationNumber(),
                entity.getRegistrationDate(),
                entity.getDeductionCycle(),
                entity.getEmployeeRate(),
                entity.getEmployerRate(),
                entity.getWageCeiling(),
                entity.isIncludeEmployerInCtc(),
                entity.isIncludeInStructure(),
                SettingSource.PERSISTED);
    }

    public static EsiSettingResponse defaultSettings(UUID tenantId) {
        return new EsiSettingResponse(
                null,
                tenantId,
                false,
                null,
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                false,
                false,
                SettingSource.DEFAULT);
    }
}
