package com.infinevo.hrms.timesheet.reminder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Who is late with the timesheet for a week (W-43.2 §3). The one query behind both reminder audiences, so the
 * reminder to the employee and the list to their manager can never disagree.
 *
 * <p>Late for the week starting {@code weekStart}, all within one tenant:
 *
 * <ol>
 *   <li>the employee is active and not deleted;
 *   <li>they have a live assignment to a live {@code STARTED} project that was running at some point in the week;
 *   <li>and they have no timesheet for the week that is {@code SUBMITTED}, {@code APPROVED} or {@code REJECTED}:
 *       none, a draft or a cancelled one is late. A rejected week is not, because the employee did submit it, and
 *       W-42.3 already tells them.
 * </ol>
 *
 * <p>One person is one row, however many projects they are on.
 */
public interface TimesheetLateQuery {

    /**
     * The employees late for the week, in name order.
     *
     * <p>Run with the tenant bound and a transaction open: the tenant binding is transaction-local.
     *
     * @param weekStart the Monday of the week
     */
    List<LateEmployee> lateEmployees(UUID tenantId, LocalDate weekStart);
}
