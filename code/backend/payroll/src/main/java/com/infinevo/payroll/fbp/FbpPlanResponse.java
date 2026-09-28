package com.infinevo.payroll.fbp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for FBP plan configuration (W-27.1).
 */
public record FbpPlanResponse(
        UUID id,
        boolean exists,
        boolean isEnabled,
        LocalDate windowOpensOn,
        LocalDate windowClosesOn,
        boolean isLocked,
        Instant lockedAt,
        boolean notifyOnRelease,
        boolean notifyOnLock,
        List<Integer> reminderDaysBeforeClose) {

    public static FbpPlanResponse defaults() {
        return new FbpPlanResponse(null, false, false, null, null, false, null, true, true, List.of(5, 1));
    }

    public static FbpPlanResponse fromEntity(FbpPlan entity) {
        if (entity == null) {
            return defaults();
        }
        return new FbpPlanResponse(
                entity.getId(),
                true,
                entity.isEnabled(),
                entity.getWindowOpensOn(),
                entity.getWindowClosesOn(),
                entity.isLocked(),
                entity.getLockedAt(),
                entity.isNotifyOnRelease(),
                entity.isNotifyOnLock(),
                entity.getReminderDaysBeforeClose());
    }
}
