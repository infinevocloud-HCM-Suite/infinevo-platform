package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.core.notification.ReminderRuleRepository;
import com.infinevo.core.notification.ReminderRuleRequest;
import com.infinevo.core.notification.ReminderRuleResponse;
import com.infinevo.core.notification.ReminderRuleServiceImpl;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for POI reminder rule creation and resolver integration (W-34.3 spec section 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Creating a {@code POI_DUE_DATE} / {@code POI_PENDING} rule is accepted (was 400 before resolvers existed).
 *   <li>Without resolvers, the rule is refused with validation errors.
 *   <li>The anchor resolver produces the due date for the year's window.
 *   <li>The audience resolver returns pending employees and excludes completed ones.
 *   <li>Notification composition for {@code POI_REMINDER} carries {@code due_date}, {@code employee_name},
 *       and {@code financial_year}.
 * </ul>
 */
class ProofReminderRuleIT extends ProofIntegrationTestBase {

    @Autowired
    private ProofDueDateAnchorResolver anchorResolver;

    @Autowired
    private ProofPendingAudienceResolver audienceResolver;

    @Autowired
    private EmployeeProofOfInvestmentRepository proofRepository;

    @Test
    @DisplayName("Creating a POI_DUE_DATE / POI_PENDING rule is accepted (was 400 without resolvers)")
    void createPoiReminderRuleAccepted() {
        ReminderRuleRepository repository = mock(ReminderRuleRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            ReminderRule entity = invocation.getArgument(0);
            return entity;
        });

        ReminderRuleServiceImpl serviceWithResolvers =
                new ReminderRuleServiceImpl(repository, List.of(audienceResolver), List.of(anchorResolver));

        ReminderRuleRequest request = new ReminderRuleRequest(
                NotificationEvent.POI_REMINDER, "POI_PENDING", Anchor.POI_DUE_DATE, 7, null, LocalTime.of(10, 0), 2, 3);

        ReminderRuleResponse created = serviceWithResolvers.create(request);
        assertThat(created).isNotNull();
        assertThat(created.event()).isEqualTo(NotificationEvent.POI_REMINDER);
        assertThat(created.audience()).isEqualTo("POI_PENDING");
        assertThat(created.anchor()).isEqualTo(Anchor.POI_DUE_DATE);
        assertThat(created.offsetDays()).isEqualTo(7);
        assertThat(created.sendAtLocalTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(created.repeatEveryDays()).isEqualTo(2);
        assertThat(created.maxRepeats()).isEqualTo(3);
    }

    @Test
    @DisplayName("Creating a POI_DUE_DATE / POI_PENDING rule without resolvers is refused with 400 validation error")
    void createPoiReminderRuleWithoutResolversRefused() {
        ReminderRuleRepository repository = mock(ReminderRuleRepository.class);

        // Service with empty resolvers (as core was before W-34.3)
        ReminderRuleServiceImpl serviceWithoutResolvers = new ReminderRuleServiceImpl(repository, List.of(), List.of());

        ReminderRuleRequest request = new ReminderRuleRequest(
                NotificationEvent.POI_REMINDER, "POI_PENDING", Anchor.POI_DUE_DATE, 7, null, LocalTime.of(10, 0), 2, 3);

        assertThatThrownBy(() -> serviceWithoutResolvers.create(request))
                .isInstanceOf(NotificationService.ValidationException.class)
                .satisfies(ex -> {
                    NotificationService.ValidationException ve = (NotificationService.ValidationException) ex;
                    assertThat(ve.fieldErrors()).containsKey("audience").containsKey("anchor");
                    assertThat(ve.fieldErrors().get("anchor"))
                            .contains("Nothing in this runtime supplies the POI_DUE_DATE date yet");
                    assertThat(ve.fieldErrors().get("audience")).contains("Unknown audience: POI_PENDING");
                });
    }

    @Test
    @DisplayName("Anchor and audience resolvers evaluate correctly and compose POI_REMINDER notifications")
    void resolversEvaluateAndComposeNotifications() throws Exception {
        openWindows();

        // Pending employee: submitted declaration, no proof row
        UUID pendingEmp =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-201", "pending@acme.com", "Grace", "Hopper");
        declare(pendingEmp);

        // Completed employee: submitted declaration, approved proof
        UUID approvedEmp =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-202", "approved@acme.com", "Ada", "Lovelace");
        declare(approvedEmp);
        actAs(approvedEmp);
        ProofResponse p = proofService.readOwn(fy);
        inTransaction(() -> {
            EmployeeProofOfInvestment proof =
                    proofRepository.findByTenantIdAndId(TENANT_A, p.id()).orElseThrow();
            proof.setStatus(ProofStatus.APPROVED);
            return proofRepository.save(proof);
        });

        ReminderRule rule = mock(ReminderRule.class);
        when(rule.getAnchor()).thenReturn(Anchor.POI_DUE_DATE);
        when(rule.getAudience()).thenReturn("POI_PENDING");
        when(rule.getEvent()).thenReturn(NotificationEvent.POI_REMINDER);
        when(rule.getId()).thenReturn(UUID.randomUUID());

        // 1. Resolve anchor date:
        Optional<LocalDate> anchorDate = anchorResolver.resolveAnchorDate(rule, TENANT_A);
        assertThat(anchorDate).isPresent();
        assertThat(anchorDate.get()).isEqualTo(today().plusDays(10));

        // 2. Resolve audience:
        List<UUID> recipients = audienceResolver.resolve(rule, TENANT_A);
        assertThat(recipients).contains(pendingEmp).doesNotContain(approvedEmp);

        // 3. Compose notification:
        for (UUID recipientId : recipients) {
            Map<String, Object> data = Map.of(
                    "employee_name",
                    "Grace Hopper",
                    "due_date",
                    anchorDate.get().toString(),
                    "financial_year",
                    fy,
                    "subject_ref",
                    "reminder_rule:" + rule.getId());
            notificationService.compose(NotificationEvent.POI_REMINDER, recipientId, data);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService, times(1))
                .compose(eq(NotificationEvent.POI_REMINDER), eq(pendingEmp), dataCaptor.capture());

        Map<String, Object> capturedData = dataCaptor.getValue();
        assertThat(capturedData.get("employee_name")).isEqualTo("Grace Hopper");
        assertThat(capturedData.get("due_date")).isEqualTo(today().plusDays(10).toString());
        assertThat(capturedData.get("financial_year")).isEqualTo(fy);
    }
}
