package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.core.notification.ReminderRuleRepository;
import com.infinevo.hrms.timesheet.reminder.LateEmployee;
import com.infinevo.hrms.timesheet.reminder.TimesheetLateAudienceResolver;
import com.infinevo.hrms.timesheet.reminder.TimesheetLateManagerAudienceResolver;
import com.infinevo.hrms.timesheet.reminder.TimesheetLateQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W-43.2 acceptance, through the real sweep: {@link ReminderEvaluator} runs the two timesheet rules of the spec's
 * flow with the real {@link TimesheetLateAudienceResolver} and {@link TimesheetLateManagerAudienceResolver} behind
 * it. Nothing in the test composes a notification itself: the only caller of {@code NotificationService.compose} is
 * the evaluator, and what it is verified against is what the resolvers chose.
 *
 * <p>The query that says who is late runs against the database in {@code TimesheetLateQueryIT}; it is stubbed
 * here, because the evaluator needs neither a database nor a tenant list for what this test proves: that a rule on
 * each audience is due on its day, reaches the right people once, carries the week that was checked, and is not
 * sent twice. Pattern: {@code ProofReminderSweepTest}.
 */
class TimesheetReminderSweepTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ASHA = UUID.randomUUID();
    private static final UUID VIKRAM = UUID.randomUUID();
    private static final UUID MEERA = UUID.randomUUID();

    /** Monday 2026-10-05, 10:30: past the 10:00 send time of a Monday rule. Last week began Monday 2026-09-28. */
    private static final Instant MONDAY = Instant.parse("2026-10-05T10:30:00Z");

    /** Wednesday 2026-10-07, 10:30. */
    private static final Instant WEDNESDAY = Instant.parse("2026-10-07T10:30:00Z");

    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 9, 28);

    private ReminderRuleRepository ruleRepository;
    private NotificationService notificationService;
    private TimesheetLateQuery lateQuery;
    private ReminderRule reminderRule;
    private ReminderRule escalationRule;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(ReminderRuleRepository.class);
        notificationService = mock(NotificationService.class);
        lateQuery = mock(TimesheetLateQuery.class);
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(1);

        // The two rules the administrator adds (spec §3): the reminder on Mondays, the escalation on Wednesdays.
        reminderRule = new ReminderRule(
                TENANT,
                NotificationEvent.TIMESHEET_REMINDER,
                TimesheetLateAudienceResolver.AUDIENCE,
                Anchor.WEEKLY,
                0,
                1,
                LocalTime.of(10, 0),
                null,
                null,
                "system");
        escalationRule = new ReminderRule(
                TENANT,
                NotificationEvent.TIMESHEET_ESCALATION,
                TimesheetLateManagerAudienceResolver.AUDIENCE,
                Anchor.WEEKLY,
                0,
                3,
                LocalTime.of(10, 0),
                null,
                null,
                "system");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(TENANT)).thenReturn(List.of(reminderRule, escalationRule));

        // Asha is late on two projects, which the query reports as one person; Vikram reports to the same manager.
        when(lateQuery.lateEmployees(eq(TENANT), eq(LAST_WEEK)))
                .thenReturn(List.of(
                        new LateEmployee(ASHA, "Asha Rao", MEERA), new LateEmployee(VIKRAM, "Vikram Sen", MEERA)));
    }

    private ReminderEvaluator evaluatorAt(Instant now) {
        return new ReminderEvaluator(
                mock(JdbcTemplate.class),
                ruleRepository,
                notificationService,
                mock(EmployeeRepository.class),
                List.of(
                        new TimesheetLateAudienceResolver(lateQuery),
                        new TimesheetLateManagerAudienceResolver(lateQuery)),
                List.of(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> data() {
        return ArgumentCaptor.forClass(Map.class);
    }

    @Test
    @DisplayName("On Monday each late employee gets one TIMESHEET_REMINDER naming last week's Monday, not this week's")
    void mondayRemindsEachLateEmployeeOnceWithLastWeek() {
        int executed = evaluatorAt(MONDAY).evaluateTenant(TENANT, ZoneOffset.UTC, MONDAY);

        assertThat(executed).as("only the Monday rule is due").isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> data = data();
        ArgumentCaptor<UUID> recipients = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, times(2))
                .compose(eq(NotificationEvent.TIMESHEET_REMINDER), recipients.capture(), data.capture());
        assertThat(recipients.getAllValues()).containsExactlyInAnyOrder(ASHA, VIKRAM);
        assertThat(data.getAllValues()).allSatisfy(values -> {
            assertThat(values.get("week_start"))
                    .as("the audience's week, not the sweep's this-week Monday of 2026-10-05")
                    .isEqualTo("2026-09-28");
            assertThat(values).containsKeys("employee_name", "subject_ref");
        });
        verify(notificationService, never()).compose(eq(NotificationEvent.TIMESHEET_ESCALATION), any(), any());
    }

    @Test
    @DisplayName("A second sweep the same day finds the slot claimed and sends nothing more")
    void aSecondSweepTheSameDaySendsNothing() {
        // The first run claims today's slot; the second finds it taken, as the conditional update returns 0 rows.
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(1, 0);
        ReminderEvaluator evaluator = evaluatorAt(MONDAY);

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, MONDAY)).isEqualTo(1);
        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, MONDAY)).isZero();

        verify(notificationService, times(2)).compose(eq(NotificationEvent.TIMESHEET_REMINDER), any(), any());
    }

    @Test
    @DisplayName("On Wednesday their manager gets one TIMESHEET_ESCALATION listing both, naming last week's Monday")
    void wednesdayGivesTheManagerOneList() {
        int executed = evaluatorAt(WEDNESDAY).evaluateTenant(TENANT, ZoneOffset.UTC, WEDNESDAY);

        assertThat(executed).as("only the Wednesday rule is due").isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> data = data();
        verify(notificationService, times(1))
                .compose(eq(NotificationEvent.TIMESHEET_ESCALATION), eq(MEERA), data.capture());
        assertThat(data.getValue())
                .containsEntry("late_employees", "Asha Rao, Vikram Sen")
                .containsEntry("week_start", "2026-09-28")
                .containsKeys("employee_name");
        verify(notificationService, never()).compose(eq(NotificationEvent.TIMESHEET_REMINDER), any(), any());
    }

    @Test
    @DisplayName("Nobody late: both rules run, claim their slot, and notify no one")
    void nobodyLateNotifiesNobody() {
        when(lateQuery.lateEmployees(any(), any())).thenReturn(List.of());

        assertThat(evaluatorAt(MONDAY).evaluateTenant(TENANT, ZoneOffset.UTC, MONDAY))
                .isEqualTo(1);
        assertThat(evaluatorAt(WEDNESDAY).evaluateTenant(TENANT, ZoneOffset.UTC, WEDNESDAY))
                .isEqualTo(1);

        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("A late employee with no manager is reminded on Monday and left out of Wednesday's escalation")
    void noManagerMeansNoEscalation() {
        when(lateQuery.lateEmployees(eq(TENANT), eq(LAST_WEEK)))
                .thenReturn(List.of(new LateEmployee(ASHA, "Asha Rao", null)));

        evaluatorAt(MONDAY).evaluateTenant(TENANT, ZoneOffset.UTC, MONDAY);
        evaluatorAt(WEDNESDAY).evaluateTenant(TENANT, ZoneOffset.UTC, WEDNESDAY);

        verify(notificationService, times(1)).compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(ASHA), any());
        verify(notificationService, never()).compose(eq(NotificationEvent.TIMESHEET_ESCALATION), any(), any());
    }

    @Test
    @DisplayName("The week is counted from the tenant-local day: Monday 00:30 in Auckland is still Sunday in UTC")
    void theWeekIsCountedInTheTenantsZone() {
        // 00:30 on Monday 2026-10-05 in Auckland (UTC+13 in October) is 11:30 on Sunday 2026-10-04 UTC.
        Instant now = Instant.parse("2026-10-04T11:30:00Z");
        ReminderRule earlyMonday = new ReminderRule(
                TENANT,
                NotificationEvent.TIMESHEET_REMINDER,
                TimesheetLateAudienceResolver.AUDIENCE,
                Anchor.WEEKLY,
                0,
                1,
                LocalTime.of(0, 15),
                null,
                null,
                "system");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(TENANT)).thenReturn(List.of(earlyMonday));

        evaluatorAt(now).evaluateTenant(TENANT, java.time.ZoneId.of("Pacific/Auckland"), now);

        // Monday 2026-10-05 locally chases the week of 2026-09-28; a server-clock count would chase 2026-09-21.
        verify(lateQuery).lateEmployees(eq(TENANT), eq(LAST_WEEK));
    }
}
