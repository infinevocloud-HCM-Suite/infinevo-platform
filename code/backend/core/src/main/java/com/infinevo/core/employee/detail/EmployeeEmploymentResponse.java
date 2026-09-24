package com.infinevo.core.employee.detail;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * An employee's working arrangement as the API returns it (W-13.2, spec section 4).
 *
 * <p>Output only: {@code id}, {@code tenantId}, {@code employeeId} and the two audit timestamps have
 * no counterpart on {@link EmployeeEmploymentRequest}.
 */
public record EmployeeEmploymentResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String payGrade,
        String workstationId,
        String timeZone,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted section. The only way one of these is built. */
    public static EmployeeEmploymentResponse from(EmployeeEmployment employment) {
        return new EmployeeEmploymentResponse(
                employment.getId(),
                employment.getTenantId(),
                employment.getEmployee().getId(),
                employment.getPayGrade(),
                employment.getWorkstationId(),
                employment.getTimeZone(),
                employment.getShiftStartTime(),
                employment.getShiftEndTime(),
                employment.getNote(),
                employment.getCreatedAt(),
                employment.getUpdatedAt());
    }
}
