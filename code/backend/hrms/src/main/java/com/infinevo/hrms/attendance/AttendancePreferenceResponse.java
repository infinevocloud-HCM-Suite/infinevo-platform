package com.infinevo.hrms.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Attendance preference response payload (W-40.1).
 *
 * <p>Returned by {@code GET /api/v1/hrms/attendance/preferences} and {@code PUT /api/v1/hrms/attendance/preferences}.
 * If the tenant has no saved row, default values are returned with {@code isDefault = true}.
 */
public record AttendancePreferenceResponse(
        UUID id,
        UUID tenantId,
        HoursCalculation hoursCalculation,
        BigDecimal fullDayMinimumHours,
        BigDecimal halfDayMinimumHours,
        Integer regularizationWindowDays,
        Integer maxRegularizationsPerMonth,
        boolean allowRegularizationWithoutSession,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt) {

    public static final BigDecimal DEFAULT_FULL_DAY_HOURS =
            BigDecimal.valueOf(9.00).setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal DEFAULT_HALF_DAY_HOURS =
            BigDecimal.valueOf(4.50).setScale(2, RoundingMode.HALF_UP);

    public static AttendancePreferenceResponse from(AttendancePreference pref) {
        return new AttendancePreferenceResponse(
                pref.getId(),
                pref.getTenantId(),
                pref.getHoursCalculation(),
                pref.getFullDayMinimumHours() != null
                        ? pref.getFullDayMinimumHours().setScale(2, RoundingMode.HALF_UP)
                        : null,
                pref.getHalfDayMinimumHours() != null
                        ? pref.getHalfDayMinimumHours().setScale(2, RoundingMode.HALF_UP)
                        : null,
                pref.getRegularizationWindowDays(),
                pref.getMaxRegularizationsPerMonth(),
                pref.isAllowRegularizationWithoutSession(),
                false,
                pref.getCreatedAt(),
                pref.getUpdatedAt());
    }

    public static AttendancePreferenceResponse defaults(UUID tenantId) {
        return new AttendancePreferenceResponse(
                null,
                tenantId,
                HoursCalculation.EVERY_SESSION,
                DEFAULT_FULL_DAY_HOURS,
                DEFAULT_HALF_DAY_HOURS,
                null,
                null,
                true,
                true,
                null,
                null);
    }
}
