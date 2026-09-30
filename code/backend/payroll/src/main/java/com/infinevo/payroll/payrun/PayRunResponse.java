package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One pay run as the API returns it (W-29.1 §4), with the counts of included and skipped employees
 * and, once computed, the three run totals (W-29.2 §4) and how many rows came out with a negative
 * net (W-29.3 §4).
 */
public record PayRunResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("period") String period,
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        @JsonProperty("cutoff_date") LocalDate cutoffDate,
        @JsonProperty("pay_date") LocalDate payDate,
        @JsonProperty("run_type") PayRunType runType,
        @JsonProperty("status") PayRunStatus status,
        @JsonProperty("included_count") int includedCount,
        @JsonProperty("skipped_count") int skippedCount,
        @JsonProperty("total_gross") BigDecimal totalGross,
        @JsonProperty("total_deductions") BigDecimal totalDeductions,
        @JsonProperty("total_net_pay") BigDecimal totalNetPay,
        @JsonProperty("negative_net_count") int negativeNetCount,
        @JsonProperty("computed_at") Instant computedAt,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("locked_at") Instant lockedAt,
        @JsonProperty("locked_by") String lockedBy,
        @JsonProperty("cancelled_at") Instant cancelledAt,
        @JsonProperty("cancelled_by") String cancelledBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static PayRunResponse from(PayRun run) {
        return new PayRunResponse(
                run.getId(),
                run.getPeriod().toString(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getCutoffDate(),
                run.getPayDate(),
                run.getRunType(),
                run.getStatus(),
                run.getIncludedCount(),
                run.getSkippedCount(),
                run.getTotalGross(),
                run.getTotalDeductions(),
                run.getTotalNetPay(),
                run.getNegativeNetCount(),
                run.getComputedAt(),
                run.getFailureReason(),
                run.getLockedAt(),
                run.getLockedBy(),
                run.getCancelledAt(),
                run.getCancelledBy(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }
}
