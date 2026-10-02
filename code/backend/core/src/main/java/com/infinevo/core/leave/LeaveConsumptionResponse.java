package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for a single leave consumption event (W-16.4a, spec section 4).
 */
public record LeaveConsumptionResponse(
        UUID id,
        UUID employeeId,
        UUID allocationId,
        UUID leaveRequestId,
        BigDecimal consumedDays,
        LocalDate consumedOn,
        String period,
        UUID reversesId,
        String reason) {

    public static LeaveConsumptionResponse from(LeaveConsumption lc) {
        return new LeaveConsumptionResponse(
                lc.getId(),
                lc.getEmployeeId(),
                lc.getAllocationId(),
                lc.getLeaveRequestId(),
                lc.getConsumedDays(),
                lc.getConsumedOn(),
                lc.getPeriod(),
                lc.getReversesId(),
                lc.getReason());
    }
}
