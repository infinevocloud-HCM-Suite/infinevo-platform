package com.infinevo.payroll.scheduled;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One scheduled earning as the Salary tab shows it (W-73.6 §4): the row, and the pay input each
 * instalment already written became, oldest first.
 */
public record ScheduledEarningResponse(
        UUID id,
        UUID employeeId,
        UUID componentId,
        String componentCode,
        String componentName,
        BigDecimal amount,
        String firstPeriod,
        int instalments,
        int paidInstalments,
        String nextPeriod,
        ScheduledEarningStatus status,
        String reason,
        String statusReason,
        List<UUID> payInputIds,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    static ScheduledEarningResponse from(
            ScheduledEarning row, String componentCode, String componentName, List<UUID> payInputIds) {
        return new ScheduledEarningResponse(
                row.getId(),
                row.getEmployeeId(),
                row.getEarningId(),
                componentCode,
                componentName,
                row.getAmount(),
                row.getFirstPeriod().toString(),
                row.getInstalments(),
                row.getPaidInstalments(),
                row.getStatus().isTerminal() ? null : row.nextPeriod().toString(),
                row.getStatus(),
                row.getReason(),
                row.getStatusReason(),
                List.copyOf(payInputIds),
                row.getCreatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
