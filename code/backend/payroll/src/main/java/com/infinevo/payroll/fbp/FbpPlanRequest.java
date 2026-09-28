package com.infinevo.payroll.fbp;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for upserting FBP plan configuration (W-27.1).
 */
public record FbpPlanRequest(
        Boolean isEnabled,
        LocalDate windowOpensOn,
        LocalDate windowClosesOn,
        Boolean notifyOnRelease,
        Boolean notifyOnLock,
        List<Integer> reminderDaysBeforeClose) {}
