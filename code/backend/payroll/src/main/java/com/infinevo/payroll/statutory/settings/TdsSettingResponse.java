package com.infinevo.payroll.statutory.settings;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for the company TDS switch (D-141). A tenant with no saved row reports the default,
 * enabled, with source {@link SettingSource#DEFAULT}.
 */
public record TdsSettingResponse(
        UUID id, UUID tenantId, boolean enabled, Instant updatedAt, String updatedBy, SettingSource source) {

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
}
