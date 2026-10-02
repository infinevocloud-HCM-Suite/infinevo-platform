package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.infinevo.payroll.proof.ProofDueDateAnchorResolver;
import com.infinevo.payroll.proof.ProofPendingAudienceResolver;
import com.infinevo.payroll.proof.ProofPendingQuery;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindowRepository;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * W-34.3 acceptance, through the real sweep: {@link ReminderEvaluator} runs a {@code POI_DUE_DATE} /
 * {@code POI_PENDING} rule with the real {@link ProofDueDateAnchorResolver} and the real
 * {@link ProofPendingAudienceResolver} behind it. Nothing in the test composes a notification itself: the
 * only caller of {@code NotificationService.compose} is the evaluator, and what it is verified against is what
 * the resolvers chose.
 *
 * <p>The two resolvers' own queries run against the database in {@code ProofPendingAudienceIT} and
 * {@code ProofReminderRuleIT}; the ports they read through ({@link ProofPendingQuery} and the window
 * repository) are stubbed here, because the evaluator needs neither a database nor a tenant list for what
 * this test proves: that a rule on the proof anchor is due in its window, reaches exactly the employees the
 * audience resolver names, and carries the deadline from the year's window, and that a rule with no deadline
 * to remind about sends nothing.
 */
class ProofReminderSweepTest {

    private static final UUID TENANT = UUID.randomUUID();

    private ReminderRuleRepository ruleRepository;
    private NotificationService notificationService;
    private IncomeTaxDeclarationWindowRepository windowRepository;
    private ProofPendingQuery pendingQuery;
    private ReminderEvaluator evaluator;
    private ReminderRule rule;
    private Instant now;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(ReminderRuleRepository.class);
        notificationService = mock(NotificationService.class);
        windowRepository = mock(IncomeTaxDeclarationWindowRepository.class);
        pendingQuery = mock(ProofPendingQuery.class);
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(1);

        now = Instant.now();
        // Sent from midnight on, so the slot is today whatever time the test runs; reminded from a week out.
        rule = new ReminderRule(
                TENANT,
                NotificationEvent.POI_REMINDER,
                ProofPendingAudienceResolver.AUDIENCE,
                Anchor.POI_DUE_DATE,
                7,
                null,
                LocalTime.MIDNIGHT,
                null,
                null,
                "system");
        when(ruleRepository.findByTenantIdAndIsActiveTrue(TENANT)).thenReturn(List.of(rule));

        evaluator = new ReminderEvaluator(
                mock(JdbcTemplate.class),
                ruleRepository,
                notificationService,
                mock(EmployeeRepository.class),
                List.of(new ProofPendingAudienceResolver(pendingQuery)),
                List.of(new ProofDueDateAnchorResolver(windowRepository)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static LocalDate today() {
        return LocalDate.now(TaxDeclarationRules.defaultClock().withZone(TaxDeclarationRules.ZONE));
    }

    private void windowDueIn(long days, boolean locked) {
        windowDueIn(days, locked, today().minusDays(10));
    }

    private void windowDueIn(long days, boolean locked, LocalDate opensOn) {
        IncomeTaxDeclarationWindow window = mock(IncomeTaxDeclarationWindow.class);
        when(window.getPoiOpensOn()).thenReturn(opensOn);
        when(window.getPoiDueDate()).thenReturn(today().plusDays(days));
        when(window.isPoiLocked()).thenReturn(locked);
        when(windowRepository.findByTenantIdAndFinancialYear(eq(TENANT), anyString()))
                .thenReturn(Optional.of(window));
    }

    @Test
    @DisplayName("Within its window the sweep reminds exactly the pending employees, with the window's due date")
    void remindsExactlyThePendingEmployees() {
        UUID pendingOne = UUID.randomUUID();
        UUID pendingTwo = UUID.randomUUID();
        windowDueIn(3, false);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(pendingOne, pendingTwo));

        int executed = evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now);

        assertThat(executed).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<UUID> recipients = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, times(2))
                .compose(eq(NotificationEvent.POI_REMINDER), recipients.capture(), data.capture());
        assertThat(recipients.getAllValues()).containsExactlyInAnyOrder(pendingOne, pendingTwo);
        assertThat(data.getAllValues()).allSatisfy(values -> {
            assertThat(values.get("due_date")).isEqualTo(today().plusDays(3).toString());
            assertThat(values.get("financial_year"))
                    .isEqualTo(FinancialYear.of(today()).label());
            assertThat(values).containsKeys("employee_name", "subject_ref");
        });
        verify(pendingQuery)
                .findPendingEmployeeIds(eq(TENANT), eq(FinancialYear.of(today()).label()));
        verify(ruleRepository).claimRun(any(), eq(TENANT), any(), any(), eq(true));
        assertThat(rule.getRepeatCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A second sweep on the same day finds the slot already claimed and sends nothing more")
    void secondRunSameDaySendsNothing() {
        windowDueIn(3, false);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(UUID.randomUUID()));
        // The first run claims today's slot; the second finds it taken, as the conditional update returns 0 rows.
        when(ruleRepository.claimRun(any(), any(), any(), any(), anyBoolean())).thenReturn(1, 0);

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isEqualTo(1);
        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        verify(notificationService, times(1)).compose(eq(NotificationEvent.POI_REMINDER), any(), any());
    }

    @Test
    @DisplayName("Before the proof window opens there is nothing the employee can submit, so no reminder")
    void proofWindowNotYetOpen() {
        windowDueIn(3, false, today().plusDays(1));
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(UUID.randomUUID()));

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("Nobody pending: the rule runs, claims its slot, and notifies no one")
    void nobodyPending() {
        windowDueIn(3, false);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of());

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isEqualTo(1);

        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("Before the window opens, the sweep does not run the rule or even ask who is pending")
    void notYetInWindow() {
        windowDueIn(30, false);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(UUID.randomUUID()));

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        verify(pendingQuery, never()).findPendingEmployeeIds(any(), any());
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("A deadline that has passed reminds nobody of anything")
    void deadlinePassed() {
        windowDueIn(-3, false);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(UUID.randomUUID()));

        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("A locked proof window, or none at all, gives the rule no deadline and the sweep sends nothing")
    void noDeadlineNoReminder() {
        windowDueIn(3, true);
        when(pendingQuery.findPendingEmployeeIds(eq(TENANT), anyString())).thenReturn(List.of(UUID.randomUUID()));
        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        when(windowRepository.findByTenantIdAndFinancialYear(eq(TENANT), anyString()))
                .thenReturn(Optional.empty());
        assertThat(evaluator.evaluateTenant(TENANT, ZoneOffset.UTC, now)).isZero();

        verify(notificationService, never()).compose(any(), any(), any());
    }
}
