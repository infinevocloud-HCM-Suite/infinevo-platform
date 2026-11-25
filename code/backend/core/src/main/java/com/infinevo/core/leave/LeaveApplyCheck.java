package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What {@link LeaveRequestService#validateApply} found for a request it accepted (W-114.8): the leave type and its
 * name, whether it is a half day, and the working days it counts. Nothing is saved.
 */
public record LeaveApplyCheck(UUID leaveTypeId, String leaveTypeName, boolean halfDay, BigDecimal workingDays) {}
