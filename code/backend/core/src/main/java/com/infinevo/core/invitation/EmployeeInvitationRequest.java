package com.infinevo.core.invitation;

import java.util.Set;
import java.util.UUID;

/**
 * Request to invite an employee to receive portal access (W-24.2).
 *
 * @param employeeId the employee to invite
 * @param roleIds optional, null means none: the extra roles granted alongside {@code employee} on acceptance
 *     (W-73.3)
 */
public record EmployeeInvitationRequest(UUID employeeId, Set<UUID> roleIds) {

    public EmployeeInvitationRequest(UUID employeeId) {
        this(employeeId, Set.of());
    }
}
