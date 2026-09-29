package com.infinevo.worker.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.notification.ReminderAnchorResolver;
import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.core.notification.ReminderRuleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit test for {@link ReminderEvaluator} (W-20.2 §7).
 *
 * <p>Covers:
 * <ul>
 *   <li>a rule due today fires
 *   <li>one due tomorrow does not
 *   <li>repeats stop at max_repeats and repeat_count advances
 *   <li>local time honoured across timezones read from tenant.timezone
 *   <li>a weekly rule fires only on day_of_week
 *   <li>an offset rule counts offset_days from its anchor
 *   <li>last_executed_at stops a rule firing twice in one day
 *   <li>a rule whose recipients cannot be resolved stays unclaimed, and does not stop the others
 * </ul>
 */
class ReminderEvaluatorTest {

    private JdbcTemplate jdbcTemplate;
    private ReminderRuleRepository ruleRepository;
    private NotificationService notificationService;
    private EmployeeRepository employeeRepository;
    private ReminderAudienceResolver audienceResolver;
    private ReminderAnchorResolver anchorResolver;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ruleRepository = mock(ReminderRuleRepository.class);
        notificationService = mock(NotificationService.class);
        employeeRepository = mock(EmployeeRepository.class);
        audienceResolver = mock(ReminderAudienceResolver.class);
        anchorResolver = mock(ReminderAnchorResolver.class);

        when(audienceResolver.audience()).thenReturn("SUBJECT");
        when(audienceResolver.resolve(any(ReminderRule.class), eq(tenantId))).thenReturn(List.of(recipientId));
        // The claim succeeds unless a test says otherwise.
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(1);
    }

    @Test
    @DisplayName("a rule due today fires; repeat_count advances and last_executed_at is updated")
    void ruleDueTodayFires() {
        // Friday 2026-10-02 10:00 UTC (dayOfWeek = 5)
        Instant fixedInstant = Instant.parse("2026-10-02T10:00:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5, // Friday
                LocalTime.of(9, 0), // scheduled for 09:00, current time is 10:00
                null,
                null,
                "system");

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                clock);

        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        int executed = evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, fixedInstant);

        assertEquals(1, executed);
        assertEquals(1, rule.getRepeatCount());
        assertNotNull(rule.getLastExecutedAt());
        verify(notificationService).compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(recipientId), any());
        verify(ruleRepository).claimRun(any(), eq(tenantId), isNull(), any(), eq(false));
    }

    @Test
    @DisplayName("one due tomorrow does not fire")
    void ruleDueTomorrowDoesNotFire() {
        // Thursday 2026-10-01 10:00 UTC (dayOfWeek = 4)
        Instant fixedInstant = Instant.parse("2026-10-01T10:00:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5, // Scheduled for Friday (tomorrow)
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                clock);

        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        int executed = evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, fixedInstant);

        assertEquals(0, executed);
        assertEquals(0, rule.getRepeatCount());
        verify(notificationService, never()).compose(any(), any(), any());
        verify(ruleRepository, never()).claimRun(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("repeats stop at max_repeats and repeat_count advances")
    void repeatsStopAtMaxRepeats() {
        Instant fixedInstant = Instant.parse("2026-10-02T10:00:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                2, // max_repeats = 2
                "system");
        rule.setRepeatCount(2); // Already reached max_repeats

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                clock);

        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        int executed = evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, fixedInstant);

        assertEquals(0, executed);
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("local time honoured across timezones read from tenant.timezone")
    void localTimeHonouredAcrossTimezones() {
        // UTC 13:30 = Asia/Kolkata 19:00 (UTC+05:30); America/New_York 09:30 (EDT, UTC-04:00)
        Instant fixedInstant = Instant.parse("2026-10-02T13:30:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        // Rule scheduled for 10:00:00 local time on Friday (dayOfWeek=5)
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(10, 0),
                null,
                null,
                "system");

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                clock);

        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        // For Kolkata (19:00 local time): 19:00 >= 10:00 -> due!
        boolean dueInKolkata = evaluator.isRuleDue(
                rule, ZoneId.of("Asia/Kolkata"), LocalDate.of(2026, 10, 2), LocalTime.of(19, 0), DayOfWeek.FRIDAY);
        assertTrue(dueInKolkata, "Rule should be due in Kolkata where local time is 19:00");

        // For New York (09:30 local time): 09:30 < 10:00 -> NOT due yet!
        boolean dueInNewYork = evaluator.isRuleDue(
                rule, ZoneId.of("America/New_York"), LocalDate.of(2026, 10, 2), LocalTime.of(9, 30), DayOfWeek.FRIDAY);
        assertFalse(dueInNewYork, "Rule should NOT be due in New York where local time is 09:30");
    }

    @Test
    @DisplayName("a weekly rule fires only on day_of_week")
    void weeklyRuleFiresOnlyOnDayOfWeek() {
        ReminderRule weeklyFriday = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5, // Friday
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.systemUTC());

        // Test on Friday
        assertTrue(evaluator.isRuleDue(
                weeklyFriday, ZoneOffset.UTC, LocalDate.of(2026, 10, 2), LocalTime.of(10, 0), DayOfWeek.FRIDAY));

        // Test on Thursday
        assertFalse(evaluator.isRuleDue(
                weeklyFriday, ZoneOffset.UTC, LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), DayOfWeek.THURSDAY));

        // Test on Saturday
        assertFalse(evaluator.isRuleDue(
                weeklyFriday, ZoneOffset.UTC, LocalDate.of(2026, 10, 3), LocalTime.of(10, 0), DayOfWeek.SATURDAY));
    }

    @Test
    @DisplayName("an offset rule counts offset_days from its anchor")
    void offsetRuleCountsOffsetDaysFromAnchor() {
        when(anchorResolver.anchor()).thenReturn(Anchor.POI_DUE_DATE);
        // Anchor deadline is 2026-10-15
        when(anchorResolver.resolveAnchorDate(any(ReminderRule.class), eq(tenantId)))
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 15)));

        // 5 days before POI_DUE_DATE -> due on 2026-10-10
        ReminderRule offsetRule = new ReminderRule(
                tenantId,
                NotificationEvent.POI_REMINDER,
                "SUBJECT",
                Anchor.POI_DUE_DATE,
                5,
                null,
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(anchorResolver),
                Clock.systemUTC());

        // On 2026-10-09 (6 days before): NOT due
        assertFalse(evaluator.isRuleDue(
                offsetRule, ZoneOffset.UTC, LocalDate.of(2026, 10, 9), LocalTime.of(10, 0), DayOfWeek.FRIDAY));

        // On 2026-10-10 (5 days before): DUE
        assertTrue(evaluator.isRuleDue(
                offsetRule, ZoneOffset.UTC, LocalDate.of(2026, 10, 10), LocalTime.of(10, 0), DayOfWeek.SATURDAY));
    }

    @Test
    @DisplayName("last_executed_at stops a rule firing twice in one day")
    void lastExecutedAtStopsRuleFiringTwiceInOneDay() {
        // Today is Friday 2026-10-02
        LocalDate today = LocalDate.of(2026, 10, 2);
        Instant now = Instant.parse("2026-10-02T15:00:00Z");

        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        // Rule already executed today at 09:05 UTC
        rule.setLastExecutedAt(Instant.parse("2026-10-02T09:05:00Z"));
        rule.setRepeatCount(1);

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.fixed(now, ZoneOffset.UTC));

        boolean due = evaluator.isRuleDue(rule, ZoneOffset.UTC, today, LocalTime.of(15, 0), DayOfWeek.FRIDAY);

        assertFalse(due, "Rule that already fired today must NOT fire again on the same day");
    }

    @Test
    @DisplayName("a 23:50 weekly rule is skipped at 23:45, fires at 00:00 the next day, and not again at 00:15")
    void lateSlotIsPickedUpByTheFirstSweepAfterMidnight() {
        // Friday 2026-10-02, rule for Fridays at 23:50
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(23, 50),
                null,
                null,
                "system");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));
        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.systemUTC());

        assertEquals(0, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, Instant.parse("2026-10-02T23:45:00Z")));

        Instant midnight = Instant.parse("2026-10-03T00:00:00Z");
        assertEquals(1, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, midnight));
        verify(ruleRepository).claimRun(any(), eq(tenantId), isNull(), eq(midnight), eq(false));

        // last_executed_at is now 00:00 Saturday, which counts as Friday's slot: not again at 00:15.
        assertEquals(0, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, Instant.parse("2026-10-03T00:15:00Z")));
        verify(notificationService, times(1)).compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(recipientId), any());
    }

    @Test
    @DisplayName("a rule saved before its send time does not fire at once as yesterday's slot")
    void aMissedSlotIsOnlyPickedUpWithinTheGrace() {
        // Friday 2026-10-02 08:00, rule for Fridays at 09:00, never run
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));
        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.systemUTC());

        assertEquals(0, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, Instant.parse("2026-10-02T08:00:00Z")));
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("an offset rule does not fire after its deadline has passed")
    void offsetRuleDoesNotFireAfterTheDeadline() {
        when(anchorResolver.anchor()).thenReturn(Anchor.POI_DUE_DATE);
        when(anchorResolver.resolveAnchorDate(any(ReminderRule.class), eq(tenantId)))
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 15)));
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.POI_REMINDER,
                "SUBJECT",
                Anchor.POI_DUE_DATE,
                5,
                null,
                LocalTime.of(9, 0),
                1,
                null,
                "system");
        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(anchorResolver),
                Clock.systemUTC());

        assertTrue(evaluator.isRuleDue(
                rule, ZoneOffset.UTC, LocalDate.of(2026, 10, 15), LocalTime.of(10, 0), DayOfWeek.THURSDAY));
        assertFalse(evaluator.isRuleDue(
                rule, ZoneOffset.UTC, LocalDate.of(2026, 10, 16), LocalTime.of(10, 0), DayOfWeek.FRIDAY));
    }

    @Test
    @DisplayName("a new deadline opens a new window: last year's run does not stop this year's first reminder")
    void newDeadlineReArmsTheRule() {
        when(anchorResolver.anchor()).thenReturn(Anchor.POI_DUE_DATE);
        when(anchorResolver.resolveAnchorDate(any(ReminderRule.class), eq(tenantId)))
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 15)));
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.POI_REMINDER,
                "SUBJECT",
                Anchor.POI_DUE_DATE,
                5,
                null,
                LocalTime.of(9, 0),
                null,
                1,
                "system");
        rule.setLastExecutedAt(Instant.parse("2025-10-10T09:00:00Z"));
        rule.setRepeatCount(1); // max_repeats reached - last year
        Instant now = Instant.parse("2026-10-10T10:00:00Z");
        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(anchorResolver),
                Clock.fixed(now, ZoneOffset.UTC));
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        assertEquals(1, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, now));

        // The count restarts for the new deadline, and due_date is the deadline itself.
        verify(ruleRepository)
                .claimRun(any(), eq(tenantId), eq(Instant.parse("2025-10-10T09:00:00Z")), eq(now), eq(true));
        verify(notificationService)
                .compose(eq(NotificationEvent.POI_REMINDER), eq(recipientId), argThat(data -> "2026-10-15"
                        .equals(data.get("due_date"))));
        assertEquals(1, rule.getRepeatCount());
    }

    @Test
    @DisplayName("a rule another sweep already claimed sends nothing")
    void claimedElsewhereSendsNothing() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(0);
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));
        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(0, evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, now));
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("recipients that cannot be resolved leave the rule unclaimed, so the next sweep tries it again")
    void resolverFailureLeavesTheRuleUnclaimed() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        when(audienceResolver.resolve(any(ReminderRule.class), eq(tenantId)))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(fridayRule()));

        assertEquals(0, evaluator(now).evaluateTenant(tenantId, ZoneOffset.UTC, now));

        verify(ruleRepository, never()).claimRun(any(), any(), any(), any(), anyBoolean());
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("one rule failing does not stop the tenant's other due rules")
    void oneFailingRuleDoesNotStopTheOthers() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        when(audienceResolver.resolve(any(ReminderRule.class), eq(tenantId)))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(recipientId));
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(fridayRule(), fridayRule()));

        assertEquals(1, evaluator(now).evaluateTenant(tenantId, ZoneOffset.UTC, now));

        verify(ruleRepository, times(1)).claimRun(any(), eq(tenantId), any(), any(), anyBoolean());
        verify(notificationService, times(1)).compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(recipientId), any());
    }

    /** A weekly rule due on Fridays at 09:00, never run. */
    private ReminderRule fridayRule() {
        return new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");
    }

    private ReminderEvaluator evaluator(Instant now) {
        return new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
