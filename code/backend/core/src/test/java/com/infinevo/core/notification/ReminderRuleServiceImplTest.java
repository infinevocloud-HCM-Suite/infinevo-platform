package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-43.1 §7, {@code ReminderRuleServiceImplTest}: a rule is accepted for an event when the sweep and the named audience
 * between them supply every value the event needs.
 */
class ReminderRuleServiceImplTest {

    private final UUID tenantId = UUID.randomUUID();
    private ReminderRuleRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(ReminderRuleRepository.class);
        when(repository.save(any(ReminderRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** An audience that supplies nothing, as {@code SUBJECT} does. */
    private static ReminderAudienceResolver plainAudience(String name) {
        return new ReminderAudienceResolver() {
            @Override
            public String audience() {
                return name;
            }

            @Override
            public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
                return List.of();
            }
        };
    }

    /** An audience that supplies the values the escalation needs, as W-43.2's will. */
    private static ReminderAudienceResolver escalatingAudience(String name) {
        return new ReminderAudienceResolver() {
            @Override
            public String audience() {
                return name;
            }

            @Override
            public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
                return List.of();
            }

            @Override
            public Set<String> suppliedPlaceholders() {
                return Set.of("week_start", "late_employees");
            }
        };
    }

    private static ReminderRuleRequest request(NotificationEvent event, String audience) {
        return new ReminderRuleRequest(event, audience, Anchor.WEEKLY, 0, 5, LocalTime.of(9, 0), null, null);
    }

    private ReminderRuleServiceImpl service(ReminderAudienceResolver... audiences) {
        return new ReminderRuleServiceImpl(repository, List.of(audiences));
    }

    @Test
    @DisplayName("TIMESHEET_ESCALATION with SUBJECT, which supplies nothing, is refused, naming late_employees")
    void escalationIsRefusedForAnAudienceThatSuppliesNothing() {
        ReminderRuleServiceImpl service = service(plainAudience("SUBJECT"));

        assertThatThrownBy(() -> service.create(request(NotificationEvent.TIMESHEET_ESCALATION, "SUBJECT")))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> {
                    assertThat(e.fieldErrors()).containsOnlyKeys("event");
                    assertThat(e.fieldErrors().get("event")).contains("late_employees");
                });
    }

    @Test
    @DisplayName("TIMESHEET_ESCALATION with a stub audience that supplies late_employees is accepted")
    void escalationIsAcceptedForAnAudienceThatSuppliesIt() {
        ReminderRuleServiceImpl service = service(plainAudience("SUBJECT"), escalatingAudience("TIMESHEET_LATE"));

        assertThatCode(() -> service.create(request(NotificationEvent.TIMESHEET_ESCALATION, "TIMESHEET_LATE")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TIMESHEET_REMINDER with SUBJECT is still accepted: the sweep supplies its values")
    void theReminderEventIsStillAcceptedForSubject() {
        ReminderRuleServiceImpl service = service(plainAudience("SUBJECT"));

        assertThatCode(() -> service.create(request(NotificationEvent.TIMESHEET_REMINDER, "SUBJECT")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An audience's values count only for rules that name that audience, not for another's")
    void anAudiencesValuesAreNotLentToOthers() {
        ReminderRuleServiceImpl service = service(plainAudience("SUBJECT"), escalatingAudience("TIMESHEET_LATE"));

        assertThatThrownBy(() -> service.create(request(NotificationEvent.TIMESHEET_ESCALATION, "SUBJECT")))
                .isInstanceOf(NotificationService.ValidationException.class);
    }

    @Test
    @DisplayName("The audience is matched without regard to case, and a blank or unknown one is refused as before")
    void audienceLookupAndItsOwnErrors() {
        ReminderRuleServiceImpl service = service(escalatingAudience("TIMESHEET_LATE"));

        assertThatCode(() -> service.create(request(NotificationEvent.TIMESHEET_ESCALATION, " timesheet_late ")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.create(request(NotificationEvent.TIMESHEET_REMINDER, "NOBODY")))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsEntry("audience", "Unknown audience: NOBODY"));
        assertThatThrownBy(() -> service.create(request(NotificationEvent.TIMESHEET_REMINDER, " ")))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsEntry("audience", "audience is required"));
    }

    @Test
    @DisplayName("An unknown audience on an event that needs an audience's values reports both, so the fix is one trip")
    void anUnknownAudienceAlsoCannotSupplyTheValues() {
        ReminderRuleServiceImpl service = service(plainAudience("SUBJECT"));

        assertThatThrownBy(() -> service.create(request(NotificationEvent.TIMESHEET_ESCALATION, "NOBODY")))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsOnlyKeys("event", "audience"));
    }

    @Test
    @DisplayName("The default audience methods give no values, and recipients are resolve()'s ids with none")
    void theDefaultsKeepAnOlderAudienceWorking() {
        UUID one = UUID.randomUUID();
        ReminderAudienceResolver older = new ReminderAudienceResolver() {
            @Override
            public String audience() {
                return "OLD";
            }

            @Override
            public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
                return List.of(one);
            }
        };

        assertThat(older.suppliedPlaceholders()).isEmpty();
        assertThat(older.recipients(null, tenantId, java.time.LocalDate.of(2026, 10, 2)))
                .containsExactly(ReminderRecipient.of(one));
        assertThat(ReminderRecipient.of(one).placeholders()).isEmpty();
        assertThat(new ReminderRecipient(one, null).placeholders()).isEmpty();
    }
}
