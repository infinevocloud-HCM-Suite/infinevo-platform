package com.infinevo.hrms.timesheet.reminder;

import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRecipient;
import com.infinevo.core.notification.ReminderRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code TIMESHEET_LATE_MANAGER} reminder audience: each primary reporting manager, given one list of their
 * late reports for last week (W-43.2 §3). One list per manager, as legacy's escalation was one list per week, and
 * not one mail per late employee.
 *
 * <p>Built from the same {@link TimesheetLateQuery} as the employee's reminder, so the two never disagree. A late
 * employee with no active primary manager on the week's last day is left out and counted in the log (decision 7).
 */
@Component
public class TimesheetLateManagerAudienceResolver implements ReminderAudienceResolver {

    public static final String AUDIENCE = "TIMESHEET_LATE_MANAGER";

    private static final Logger log = LoggerFactory.getLogger(TimesheetLateManagerAudienceResolver.class);

    private final TimesheetLateQuery lateQuery;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    TimesheetLateManagerAudienceResolver(TimesheetLateQuery lateQuery, Clock clock) {
        this.lateQuery = Objects.requireNonNull(lateQuery, "lateQuery must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Autowired
    public TimesheetLateManagerAudienceResolver(TimesheetLateQuery lateQuery) {
        this(lateQuery, Clock.systemDefaultZone());
    }

    @Override
    public String audience() {
        return AUDIENCE;
    }

    @Override
    public Set<String> suppliedPlaceholders() {
        return Set.of("week_start", "late_employees");
    }

    /** The managers with late reports for last week as of today, when no slot date is known. */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
        return recipientsFor(tenantId, ReminderWeek.weekStart(LocalDate.now(clock))).stream()
                .map(ReminderRecipient::employeeId)
                .toList();
    }

    /** One recipient per manager, with the week and their late reports' names, joined with {@code ", "} in name order. */
    @Override
    @Transactional(readOnly = true)
    public List<ReminderRecipient> recipients(ReminderRule rule, UUID tenantId, LocalDate slotDate) {
        return recipientsFor(tenantId, ReminderWeek.weekStart(slotDate));
    }

    private List<ReminderRecipient> recipientsFor(UUID tenantId, LocalDate weekStart) {
        List<LateEmployee> late = lateQuery.lateEmployees(tenantId, weekStart);

        // The query returns names in order, so each manager's list is already in name order.
        Map<UUID, List<String>> namesByManager = new LinkedHashMap<>();
        int withoutManager = 0;
        for (LateEmployee employee : late) {
            if (employee.primaryManagerId() == null) {
                withoutManager++;
                continue;
            }
            namesByManager
                    .computeIfAbsent(employee.primaryManagerId(), manager -> new ArrayList<>())
                    .add(employee.name());
        }
        if (withoutManager > 0) {
            log.info(
                    "{} employee(s) late for the week of {} in tenant {} have no active primary manager and are"
                            + " left out of the escalation",
                    withoutManager,
                    weekStart,
                    tenantId);
        }

        String week = weekStart.toString();
        List<ReminderRecipient> recipients = new ArrayList<>();
        namesByManager.forEach((manager, names) -> recipients.add(new ReminderRecipient(
                manager, Map.of("week_start", week, "late_employees", String.join(", ", names)))));
        return recipients;
    }
}
