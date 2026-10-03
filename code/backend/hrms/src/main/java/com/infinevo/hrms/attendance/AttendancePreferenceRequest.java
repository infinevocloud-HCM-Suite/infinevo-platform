package com.infinevo.hrms.attendance;

import java.math.BigDecimal;

/**
 * Request payload for saving tenant attendance preferences (W-40.1).
 *
 * @param hoursCalculation method used to calculate daily work hours
 * @param fullDayMinimumHours minimum hours to qualify for a full day present
 * @param halfDayMinimumHours minimum hours to qualify for a half day present
 * @param regularizationWindowDays window in days within which regularizations can be raised (null = anytime)
 * @param maxRegularizationsPerMonth max regularization requests allowed per month (null = no limit)
 * @param allowRegularizationWithoutSession whether regularization is permitted on days with no recorded sessions
 */
public record AttendancePreferenceRequest(
        HoursCalculation hoursCalculation,
        BigDecimal fullDayMinimumHours,
        BigDecimal halfDayMinimumHours,
        Integer regularizationWindowDays,
        Integer maxRegularizationsPerMonth,
        Boolean allowRegularizationWithoutSession) {}
