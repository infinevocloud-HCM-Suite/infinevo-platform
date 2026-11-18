package com.infinevo.hrms.performance;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representation of an employee performance review (W-106.1).
 */
public record PerformanceReviewResponse(
        UUID id,
        UUID tenantId,
        UUID cycleId,
        String cycleName,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        UUID managerId,
        String managerName,
        Integer selfRating,
        String selfComments,
        Instant selfSubmittedAt,
        Integer managerRating,
        String managerComments,
        Instant managerSubmittedAt,
        PerformanceReviewStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PerformanceReviewResponse from(
            PerformanceReview review,
            String cycleName,
            String employeeName,
            String employeeNumber,
            String managerName) {
        return new PerformanceReviewResponse(
                review.getId(),
                review.getTenantId(),
                review.getCycleId(),
                cycleName,
                review.getEmployeeId(),
                employeeName,
                employeeNumber,
                review.getManagerId(),
                managerName,
                review.getSelfRating(),
                review.getSelfComments(),
                review.getSelfSubmittedAt(),
                review.getManagerRating(),
                review.getManagerComments(),
                review.getManagerSubmittedAt(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
