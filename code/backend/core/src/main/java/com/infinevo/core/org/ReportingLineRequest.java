package com.infinevo.core.org;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Request payload for creating or updating a reporting line assignment (W-14.2).
 */
public record ReportingLineRequest(
        UUID managerId, ReportingLineKind kind, LocalDate effectiveFrom, LocalDate effectiveTo) {

    public ReportingLineRequest {
        Objects.requireNonNull(managerId, "managerId must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }

    public ReportingLineKind resolvedKind() {
        return kind != null ? kind : ReportingLineKind.PRIMARY;
    }
}
