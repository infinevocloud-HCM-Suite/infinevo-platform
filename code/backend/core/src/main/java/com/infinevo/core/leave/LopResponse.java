package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing monthly LOP details with individual delta rows (W-16.4a, spec section 4).
 */
public record LopResponse(UUID employeeId, String period, BigDecimal totalLopDays, List<LopDeltaItem> deltaRows) {

    public record LopDeltaItem(
            UUID id,
            String period,
            UUID leaveTypeId,
            UUID leaveRequestId,
            BigDecimal lopDays,
            UUID reversesId,
            UUID payInputId,
            Instant createdAt) {

        public static LopDeltaItem from(LeaveMonthlyLop l) {
            return new LopDeltaItem(
                    l.getId(),
                    l.getPeriod(),
                    l.getLeaveTypeId(),
                    l.getLeaveRequestId(),
                    l.getLopDays(),
                    l.getReversesId(),
                    l.getPayInputId(),
                    l.getCreatedAt());
        }
    }
}
