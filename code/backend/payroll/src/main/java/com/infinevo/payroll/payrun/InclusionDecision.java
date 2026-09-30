package com.infinevo.payroll.payrun;

import java.util.Objects;
import java.util.UUID;

/**
 * What the inclusion rule decided for one considered employee — the row to write (W-29.1 §3).
 * The constructor holds the same invariant as the table's {@code CHECK}: a skipped row has a reason,
 * an included row has a salary version and no reason.
 */
public record InclusionDecision(
        UUID employeeId, InclusionStatus inclusionStatus, SkipReason skipReason, UUID salaryVersionId) {

    public InclusionDecision {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(inclusionStatus, "inclusionStatus must not be null");
        if (inclusionStatus == InclusionStatus.SKIPPED && (skipReason == null || salaryVersionId != null)) {
            throw new IllegalArgumentException("A skipped row needs a reason and no salary version");
        }
        if (inclusionStatus == InclusionStatus.INCLUDED && (skipReason != null || salaryVersionId == null)) {
            throw new IllegalArgumentException("An included row needs a salary version and no reason");
        }
    }

    public static InclusionDecision included(UUID employeeId, UUID salaryVersionId) {
        return new InclusionDecision(employeeId, InclusionStatus.INCLUDED, null, salaryVersionId);
    }

    public static InclusionDecision skipped(UUID employeeId, SkipReason reason) {
        return new InclusionDecision(employeeId, InclusionStatus.SKIPPED, reason, null);
    }
}
