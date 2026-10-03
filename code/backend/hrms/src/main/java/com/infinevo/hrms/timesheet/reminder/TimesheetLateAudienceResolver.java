package com.infinevo.hrms.timesheet.reminder;

import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRecipient;
import com.infinevo.core.notification.ReminderRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code TIMESHEET_LATE} reminder audience: employees who are late with last week's timesheet, each told which
 * week (W-43.2 §3). Who is late is {@link TimesheetLateQuery}'s, the same answer the manager's list is built from.
 *
 * <p>The audience supplies {@code week_start} itself, because only it knows which week it checked: the sweep's own
 * {@code week_start} is this week's Monday (W-43.1).
 *
 * <p>Each call opens its own read-only transaction: the sweep calls an audience with the tenant bound and none open,
 * and the tenant binding only holds inside one.
 */
@Component
public class TimesheetLateAudienceResolver implements ReminderAudienceResolver {

    public static final String AUDIENCE = "TIMESHEET_LATE";

    private final TimesheetLateQuery lateQuery;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    TimesheetLateAudienceResolver(TimesheetLateQuery lateQuery, Clock clock) {
        this.lateQuery = Objects.requireNonNull(lateQuery, "lateQuery must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Autowired
    public TimesheetLateAudienceResolver(TimesheetLateQuery lateQuery) {
        this(lateQuery, Clock.systemDefaultZone());
    }

    @Override
    public String audience() {
        return AUDIENCE;
    }

    @Override
    public Set<String> suppliedPlaceholders() {
        return Set.of("week_start");
    }

    /** The late employees for last week as of today, when no slot date is known. */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
        LocalDate week = ReminderWeek.weekStart(LocalDate.now(clock));
        return lateQuery.lateEmployees(tenantId, week).stream()
                .map(LateEmployee::employeeId)
                .toList();
    }

    /** One recipient per late employee, however many projects they are on, each with the week that was checked. */
    @Override
    @Transactional(readOnly = true)
    public List<ReminderRecipient> recipients(ReminderRule rule, UUID tenantId, LocalDate slotDate) {
        String week = ReminderWeek.weekStart(slotDate).toString();
        return lateQuery.lateEmployees(tenantId, ReminderWeek.weekStart(slotDate)).stream()
                .map(late -> new ReminderRecipient(late.employeeId(), Map.of("week_start", week)))
                .toList();
    }
}
