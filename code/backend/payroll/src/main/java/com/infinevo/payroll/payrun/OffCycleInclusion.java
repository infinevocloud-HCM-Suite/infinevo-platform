package com.infinevo.payroll.payrun;

import java.util.Objects;
import java.util.UUID;

/**
 * One named employee's row on an off-cycle run (W-30.2 §3). Unlike {@link InclusionDecision} an
 * included row may carry no salary version: an off-cycle run computes from inputs only, so a joining
 * bonus before the CTC is entered is paid. The version is recorded when one is in force.
 */
public record OffCycleInclusion(
        UUID employeeId, InclusionStatus inclusionStatus, SkipReason skipReason, UUID salaryVersionId) {

    public OffCycleInclusion {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(inclusionStatus, "inclusionStatus must not be null");
        if (inclusionStatus == InclusionStatus.SKIPPED && (skipReason == null || salaryVersionId != null)) {
            throw new IllegalArgumentException("A skipped row needs a reason and no salary version");
        }
        if (inclusionStatus == InclusionStatus.INCLUDED && skipReason != null) {
            throw new IllegalArgumentException("An included row carries no reason");
        }
    }

    /** {@code salaryVersionId} may be null. */
    public static OffCycleInclusion included(UUID employeeId, UUID salaryVersionId) {
        return new OffCycleInclusion(employeeId, InclusionStatus.INCLUDED, null, salaryVersionId);
    }

    public static OffCycleInclusion skipped(UUID employeeId, SkipReason reason) {
        return new OffCycleInclusion(employeeId, InclusionStatus.SKIPPED, reason, null);
    }
}
