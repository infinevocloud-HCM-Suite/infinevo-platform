package com.infinevo.core.invitation;

import java.util.UUID;

/**
 * Request to invite an employee to receive portal access (W-24.2).
 */
public record EmployeeInvitationRequest(UUID employeeId) {}
