package com.infinevo.payroll.statutory.settings;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for the company TDS switch (D-141). A tenant with no saved row reports the default,
 * enabled, with source {@link SettingSource#DEFAULT}.
 *
 * <p>D-145: the settings screen read also carries {@code applicableCount} - employees TDS applies to - out
 * of {@code employeeCount} employed today. Both are {@code null} on the pay run's read and on save.
 */
public record TdsSettingResponse(
        UUID id,
        UUID tenantId,
        boolean enabled,
        Instant updatedAt,
        String updatedBy,
        SettingSource source,
        Integer applicableCount,
        Integer employeeCount) {

    public TdsSettingResponse(
            UUID id, UUID tenantId, boolean enabled, Instant updatedAt, String updatedBy, SettingSource source) {
        this(id, tenantId, enabled, updatedAt, updatedBy, source, null, null);
    }

    public static TdsSettingResponse from(TdsSetting entity) {
        return new TdsSettingResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.isEnabled(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                SettingSource.PERSISTED);
    }

    public static TdsSettingResponse defaultSettings(UUID tenantId) {
        return new TdsSettingResponse(null, tenantId, true, null, null, SettingSource.DEFAULT);
    }

    /** This response with the employee counts (D-145). */
    public TdsSettingResponse withCounts(int applicable, int employees) {
        return new TdsSettingResponse(id, tenantId, enabled, updatedAt, updatedBy, source, applicable, employees);
    }
}
