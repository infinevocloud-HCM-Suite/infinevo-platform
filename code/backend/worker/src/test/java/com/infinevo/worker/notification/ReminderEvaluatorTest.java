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
import com.infinevo.core.notification.ReminderRecipient;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        // A mock does not run an interface's default methods; the sweep calls recipients(), whose default (W-43.1) is
        // resolve() with no values, which is the behaviour every audience written before it keeps.
        when(audienceResolver.recipients(any(ReminderRule.class), eq(tenantId), any(LocalDate.class)))
                .thenCallRealMethod();
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

    @Test
    @DisplayName(
            "POI_REMINDER rule with POI_DUE_DATE and POI_PENDING composes one reminder per pending employee with due_date set; a second run composes none")
    void poiReminderRuleEvaluatesAndSecondRunComposesNone() {
        // Friday 2027-01-15 10:00 UTC. Due date is 2027-01-22 (7 days later). Offset is 7 days before due date.
        Instant now = Instant.parse("2027-01-15T10:00:00Z");
        LocalDate dueDate = LocalDate.of(2027, 1, 22);

        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.POI_REMINDER,
                "POI_PENDING",
                Anchor.POI_DUE_DATE,
                7, // 7 days before anchor
                null,
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        when(anchorResolver.anchor()).thenReturn(Anchor.POI_DUE_DATE);
        when(anchorResolver.resolveAnchorDate(rule, tenantId)).thenReturn(Optional.of(dueDate));

        when(audienceResolver.audience()).thenReturn("POI_PENDING");
        when(audienceResolver.resolve(rule, tenantId)).thenReturn(List.of(recipientId));

        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(rule));

        ReminderEvaluator evaluator = new ReminderEvaluator(
                jdbcTemplate,
                ruleRepository,
                notificationService,
                employeeRepository,
                List.of(audienceResolver),
                List.of(anchorResolver),
                Clock.fixed(now, ZoneOffset.UTC));

        // First run inside the window composes notification with due_date:
        int executed = evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, now);
        assertEquals(1, executed);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).compose(eq(NotificationEvent.POI_REMINDER), eq(recipientId), dataCaptor.capture());
        assertEquals("2027-01-22", dataCaptor.getValue().get("due_date"));
        assertEquals("2026-2027", dataCaptor.getValue().get("financial_year"));

        // Second run the same day (repeatCount advanced and lastExecutedAt set):
        rule.setLastExecutedAt(now);
        rule.setRepeatCount(1);
        int executedSecond = evaluator.evaluateTenant(tenantId, ZoneOffset.UTC, now.plusSeconds(3600));
        assertEquals(0, executedSecond);
        verify(notificationService, times(1)).compose(any(), any(), any()); // Still 1 call, none added
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

    // ── W-43.1: an audience supplies its own values ──────────────────────────────────────────

    private ReminderRule escalationRule() {
        return new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_ESCALATION,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> composedData(NotificationEvent event) {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).compose(eq(event), eq(recipientId), data.capture());
        return data.getValue();
    }

    @Test
    @DisplayName("a stub audience's week_start replaces the sweep's, and its late_employees reaches compose")
    void anAudiencesValuesReachComposeAndWinOnAClash() {
        // Friday 2026-10-02: the sweep's own week_start would be this week's Monday, 2026-09-28.
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        when(audienceResolver.recipients(any(ReminderRule.class), eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of(new ReminderRecipient(
                        recipientId, Map.of("week_start", "2026-09-21", "late_employees", "Asha Rao, Ravi Nair"))));
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(escalationRule()));

        assertEquals(1, evaluator(now).evaluateTenant(tenantId, ZoneOffset.UTC, now));

        Map<String, Object> data = composedData(NotificationEvent.TIMESHEET_ESCALATION);
        assertEquals("2026-09-21", data.get("week_start"), "the audience's week, not this week's Monday");
        assertEquals("Asha Rao, Ravi Nair", data.get("late_employees"));
        assertTrue(data.containsKey("employee_name"), "the sweep still supplies the name");
        assertEquals("2026-10-02", data.get("due_date"), "and every value the audience did not name");
    }

    @Test
    @DisplayName("an audience that supplies nothing gets exactly the values the sweep always filled in")
    void anAudienceWithNoOverrideGetsTodaysValues() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(fridayRule()));

        assertEquals(1, evaluator(now).evaluateTenant(tenantId, ZoneOffset.UTC, now));

        Map<String, Object> data = composedData(NotificationEvent.TIMESHEET_REMINDER);
        assertEquals(
                java.util.Set.of(
                        "employee_name",
                        "due_date",
                        "week_start",
                        "financial_year",
                        "period",
                        com.infinevo.core.notification.NotificationService.SUBJECT_REF),
                data.keySet());
        assertEquals("2026-09-28", data.get("week_start"), "this week's Monday, as before");
    }

    @Test
    @DisplayName("each recipient gets their own values: one audience, two managers, two lists")
    void eachRecipientGetsTheirOwnValues() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        UUID other = UUID.randomUUID();
        when(audienceResolver.recipients(any(ReminderRule.class), eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of(
                        new ReminderRecipient(recipientId, Map.of("late_employees", "Asha")),
                        new ReminderRecipient(other, Map.of("late_employees", "Ravi"))));
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(escalationRule()));

        evaluator(now).evaluateTenant(tenantId, ZoneOffset.UTC, now);

        assertEquals(
                "Asha", composedData(NotificationEvent.TIMESHEET_ESCALATION).get("late_employees"));
        ArgumentCaptor<Map<String, Object>> second = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).compose(eq(NotificationEvent.TIMESHEET_ESCALATION), eq(other), second.capture());
        assertEquals("Ravi", second.getValue().get("late_employees"));
    }

    @Test
    @DisplayName(
            "the slot date passed is the tenant-local day, not the server's: 23:55 Friday in Los Angeles is Saturday UTC")
    void theSlotDatePassedIsTenantLocal() {
        // 23:55 on Friday 2026-10-02 in Los Angeles (UTC-7) is already 06:55 on Saturday 2026-10-03 UTC.
        Instant now = Instant.parse("2026-10-03T06:55:00Z");
        ReminderRule lateFridayRule = new ReminderRule(
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
        when(ruleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(lateFridayRule));

        assertEquals(1, evaluator(now).evaluateTenant(tenantId, ZoneId.of("America/Los_Angeles"), now));

        verify(audienceResolver).recipients(any(ReminderRule.class), eq(tenantId), eq(LocalDate.of(2026, 10, 2)));
    }
}
