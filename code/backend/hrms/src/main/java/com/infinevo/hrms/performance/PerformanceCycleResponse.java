package com.infinevo.hrms.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a performance review cycle (W-106.1).
 */
public record PerformanceCycleResponse(
        UUID id,
        UUID tenantId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate selfReviewDeadline,
        LocalDate managerReviewDeadline,
        PerformanceCycleStatus status,
        int ratingScaleMax,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static PerformanceCycleResponse from(PerformanceCycle cycle) {
        return new PerformanceCycleResponse(
                cycle.getId(),
                cycle.getTenantId(),
                cycle.getName(),
                cycle.getPeriodStart(),
                cycle.getPeriodEnd(),
                cycle.getSelfReviewDeadline(),
                cycle.getManagerReviewDeadline(),
                cycle.getStatus(),
                cycle.getRatingScaleMax(),
                cycle.getCreatedAt(),
                cycle.getCreatedBy(),
                cycle.getUpdatedAt(),
                cycle.getUpdatedBy());
    }
}
