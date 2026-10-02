package com.infinevo.hrms.timesheet.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.core.notification.ReminderRuleRepository;
import com.infinevo.core.notification.ReminderRuleRequest;
import com.infinevo.core.notification.ReminderRuleServiceImpl;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-43.2 §7, {@code TimesheetReminderRuleIT}: the two audiences are accepted by core's rule validation, with the
 * values they supply, and refused where they supply too little. The rule service is the real one, built with the
 * real resolvers; the rule table behind it is a stand-in, as the validation reads no row.
 */
class TimesheetReminderRuleIT {

    private final UUID tenantId = UUID.randomUUID();
    private ReminderRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        ReminderRuleRepository repository = mock(ReminderRuleRepository.class);
        when(repository.save(any(ReminderRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TimesheetLateQuery query = mock(TimesheetLateQuery.class);
        service = new ReminderRuleServiceImpl(
                repository,
                List.of(
                        everyone(),
                        new TimesheetLateAudienceResolver(query),
                        new TimesheetLateManagerAudienceResolver(query)));
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** The SUBJECT audience, which supplies nothing: every active employee, as core's own does. */
    private static ReminderAudienceResolver everyone() {
        return new ReminderAudienceResolver() {
            @Override
            public String audience() {
                return "SUBJECT";
            }

            @Override
            public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
                return List.of();
            }
        };
    }

    private static ReminderRuleRequest rule(NotificationEvent event, String audience, int dayOfWeek) {
        return new ReminderRuleRequest(event, audience, Anchor.WEEKLY, 0, dayOfWeek, LocalTime.of(10, 0), null, null);
    }

    @Test
    @DisplayName(
            "Both rules of the spec's flow are accepted: the reminder to the late, the escalation to their managers")
    void bothRulesAreAccepted() {
        assertThatCode(() -> service.create(rule(NotificationEvent.TIMESHEET_REMINDER, "TIMESHEET_LATE", 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.create(rule(NotificationEvent.TIMESHEET_ESCALATION, "TIMESHEET_LATE_MANAGER", 3)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TIMESHEET_ESCALATION with TIMESHEET_LATE is refused: that audience does not supply late_employees")
    void escalationNeedsTheManagerAudience() {
        assertThatThrownBy(() -> service.create(rule(NotificationEvent.TIMESHEET_ESCALATION, "TIMESHEET_LATE", 3)))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> {
                    assertThat(e.fieldErrors()).containsOnlyKeys("event");
                    assertThat(e.fieldErrors().get("event")).contains("late_employees");
                });
    }

    @Test
    @DisplayName("The audiences are named as the spec names them, and supply what the events need")
    void theAudiencesSupplyWhatTheirEventsNeed() {
        TimesheetLateQuery query = mock(TimesheetLateQuery.class);

        assertThat(new TimesheetLateAudienceResolver(query).audience()).isEqualTo("TIMESHEET_LATE");
        assertThat(new TimesheetLateManagerAudienceResolver(query).audience()).isEqualTo("TIMESHEET_LATE_MANAGER");
        assertThat(new TimesheetLateAudienceResolver(query).suppliedPlaceholders())
                .containsExactly("week_start");
        assertThat(new TimesheetLateManagerAudienceResolver(query).suppliedPlaceholders())
                .containsExactlyInAnyOrder("week_start", "late_employees");
        assertThat(NotificationEvent.TIMESHEET_ESCALATION.placeholders())
                .as("everything the event needs is the audience's or the sweep's")
                .isSubsetOf(java.util.stream.Stream.concat(
                                new TimesheetLateManagerAudienceResolver(query).suppliedPlaceholders().stream(),
                                com.infinevo.core.notification.ReminderRuleService.SUPPLIED_PLACEHOLDERS.stream())
                        .toList());
    }
}
