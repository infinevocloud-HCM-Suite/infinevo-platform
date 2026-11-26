package com.infinevo.core.employee;

import java.util.UUID;

/**
 * Published inside the transaction that creates an employee (W-102), so onboarding can give the new joiner a
 * checklist without {@code EmployeeServiceImpl} depending on it. A listener that fails rolls the create back.
 */
public record EmployeeCreatedEvent(UUID tenantId, UUID employeeId, String actor) {}
