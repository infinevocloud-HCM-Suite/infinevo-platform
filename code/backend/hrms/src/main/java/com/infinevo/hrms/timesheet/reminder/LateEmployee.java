package com.infinevo.hrms.timesheet.reminder;

import java.util.UUID;

/**
 * An employee who is late with the timesheet for a week (W-43.2).
 *
 * @param employeeId the employee
 * @param name first and last name as the mail shows them
 * @param primaryManagerId their primary reporting manager on the week's last day, who is active; {@code null} when
 *     they have none, in which case the escalation leaves them out
 */
public record LateEmployee(UUID employeeId, String name, UUID primaryManagerId) {}
