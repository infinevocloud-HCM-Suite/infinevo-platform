package com.infinevo.core.wfh;

import java.math.BigDecimal;

/**
 * What {@link WfhRequestService#validateApply} found for a request it accepted (W-114.17): the working days it counts,
 * whether it is a half day and which half (lower case). Nothing is saved.
 */
public record WfhApplyCheck(BigDecimal workingDays, boolean halfDay, String halfDayPeriod) {}
