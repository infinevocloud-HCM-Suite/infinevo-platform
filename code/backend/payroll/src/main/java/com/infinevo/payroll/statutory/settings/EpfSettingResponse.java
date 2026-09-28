package com.infinevo.payroll.statutory.settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for EPF settings (W-31.1).
 */
public record EpfSettingResponse(
        UUID id,
        UUID tenantId,
        boolean isEnabled,
        String registrationNumber,
        LocalDate registrationDate,
        DeductionCycle deductionCycle,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal epsRate,
        BigDecimal edliRate,
        BigDecimal adminChargeRate,
        BigDecimal wageCeiling,
        boolean restrictEmployeeToCeiling,
        boolean restrictEmployerToCeiling,
        boolean prorateRestrictedWage,
        boolean considerEarnedWage,
        int epsSeniorAge,
        boolean includeEmployerInCtc,
        boolean includeEdliAdminInCtc,
        boolean includeEmployerInStructure,
        boolean includeEdliAdminInStructure,
        boolean abryScheme,
        SettingSource source) {

    public static EpfSettingResponse from(EpfSetting entity) {
        return new EpfSettingResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.isEnabled(),
                entity.getRegistrationNumber(),
                entity.getRegistrationDate(),
                entity.getDeductionCycle(),
                entity.getEmployeeRate(),
                entity.getEmployerRate(),
                entity.getEpsRate(),
                entity.getEdliRate(),
                entity.getAdminChargeRate(),
                entity.getWageCeiling(),
                entity.isRestrictEmployeeToCeiling(),
                entity.isRestrictEmployerToCeiling(),
                entity.isProrateRestrictedWage(),
                entity.isConsiderEarnedWage(),
                entity.getEpsSeniorAge(),
                entity.isIncludeEmployerInCtc(),
                entity.isIncludeEdliAdminInCtc(),
                entity.isIncludeEmployerInStructure(),
                entity.isIncludeEdliAdminInStructure(),
                entity.isAbryScheme(),
                SettingSource.PERSISTED);
    }

    public static EpfSettingResponse defaultSettings(UUID tenantId) {
        return new EpfSettingResponse(
                null,
                tenantId,
                false,
                null,
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                false,
                false,
                false,
                true,
                58,
                false,
                false,
                false,
                false,
                false,
                SettingSource.DEFAULT);
    }
}
