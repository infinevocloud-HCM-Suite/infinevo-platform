package com.infinevo.core.employee;

import java.util.UUID;

/**
 * Request body for linking or unlinking an employee to a user account login (W-13.4).
 *
 * @param userAccountId the user account ID to link, or null to clear the link
 */
public record EmployeeLoginRequest(UUID userAccountId) {}
