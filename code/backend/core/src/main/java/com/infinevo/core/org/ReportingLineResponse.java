package com.infinevo.core.org;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for a reporting line assignment (W-14.2).
 */
public record ReportingLineResponse(
        UUID id,
        UUID employeeId,
        UUID managerId,
        String managerName,
        ReportingLineKind kind,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    public static ReportingLineResponse from(ReportingLine line) {
        String managerName = line.getManager().getFirstName()
                + (line.getManager().getLastName() != null
                        ? " " + line.getManager().getLastName()
                        : "");
        return new ReportingLineResponse(
                line.getId(),
                line.getEmployee().getId(),
                line.getManager().getId(),
                managerName,
                line.getKind(),
                line.getEffectiveFrom(),
                line.getEffectiveTo());
    }
}
