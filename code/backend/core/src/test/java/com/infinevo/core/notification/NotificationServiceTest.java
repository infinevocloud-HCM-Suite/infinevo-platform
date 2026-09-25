package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * W-20.1 spec section 7 — no template for an event is an error, not a silent skip; the rendered body
 * is stored. Plus what the service decides: which channels a recipient gets, that an email waits for
 * the commit before it is queued, and that a missing queue loses nothing.
 */
class NotificationServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private NotificationRepository notifications;
    private NotificationTemplateRepository templates;
    private EmployeeRepository employees;
    private NotificationRecipientResolver recipients;
    private QueueProducer queue;
    private AtomicReference<QueueProducer> producer;
    private List<Notification> saved;
    private NotificationServiceImpl service;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        templates = mock(NotificationTemplateRepository.class);
        employees = mock(EmployeeRepository.class);
        recipients = mock(NotificationRecipientResolver.class);
        queue = mock(QueueProducer.class);
        producer = new AtomicReference<>(queue);
        saved = new ArrayList<>();
        service = new NotificationServiceImpl(
                notifications,
                templates,
                employees,
                new TemplateRenderer(),
                recipients,
                producer::get,
                Clock.fixed(NOW, ZoneOffset.UTC));

        employeeId = UUID.randomUUID();
        Employee employee = mock(Employee.class);
        when(employee.getId()).thenReturn(employeeId);
        when(employee.getWorkEmail()).thenReturn("asha@acme.test");
        when(employees.findByIdAndTenantIdAndDeletedFalse(employeeId, TENANT)).thenReturn(Optional.of(employee));

        template(NotificationEvent.LEAVE_APPROVED, Channel.IN_APP, null, "Your ${leave_type} was approved.");
        template(
                NotificationEvent.LEAVE_APPROVED,
                Channel.EMAIL,
                "Leave approved for ${employee_name}",
                "<p>Hello ${employee_name}, your ${leave_type} was approved.</p>");
        template(NotificationEvent.USER_INVITATION, Channel.EMAIL, "Join ${tenant_name}", "<p>${link}</p>");

        when(notifications.saveAll(anyIterable())).thenAnswer(inv -> {
            List<Notification> out = new ArrayList<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) {
                Notification n = (Notification) o;
                ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
                out.add(n);
            }
            saved.addAll(out);
            return out;
        });
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("An employee gets both channels: in-app stored as SENT, email QUEUED, each rendered and stored")
    void employeeGetsBothChannels() {
        List<UUID> ids = service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values());

        assertThat(ids).hasSize(2);
        Notification inApp = saved.get(0);
        Notification email = saved.get(1);
        assertThat(inApp.getChannel()).isEqualTo(Channel.IN_APP);
        assertThat(inApp.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(inApp.getBody()).isEqualTo("Your Casual <leave> was approved.");
        assertThat(inApp.getRecipientEmployeeId()).isEqualTo(employeeId);
        assertThat(email.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(email.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(email.getRecipientEmail()).isEqualTo("asha@acme.test");
        assertThat(email.getSubject()).isEqualTo("Leave approved for Asha");
        assertThat(email.getBody())
                .as("employee text is escaped into the email body")
                .isEqualTo("<p>Hello Asha, your Casual &lt;leave&gt; was approved.</p>");
        assertThat(email.getTemplateId()).isNotNull();
        assertThat(email.getQueuedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
            "Outside a transaction the email is queued at once: one message, on the notification queue, carrying its id")
    void emailIsQueuedOnce() {
        service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueueMessage<String>> message = ArgumentCaptor.forClass(QueueMessage.class);
        verify(queue, times(1)).send(eq(NotificationService.QUEUE), message.capture());
        assertThat(message.getValue().getPayload())
                .isEqualTo(saved.get(1).getId().toString());
        assertThat(message.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(message.getValue().getQueueName()).isEqualTo("notification");
    }

    @Test
    @DisplayName("Inside a transaction nothing is queued until it commits")
    void emailWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values());
        verify(queue, never()).send(any(), any());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(queue, times(1)).send(eq(NotificationService.QUEUE), any());
    }

    @Test
    @DisplayName("With no queue producer in the runtime (app before W-52.1) the email is written and stays QUEUED")
    void noProducerLosesNothing() {
        producer.set(null);

        List<UUID> ids = service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values());

        assertThat(ids).hasSize(2);
        assertThat(saved.get(1).getStatus()).isEqualTo(NotificationStatus.QUEUED);
    }

    @Test
    @DisplayName("A queue that refuses the message does not fail the request - the row is the outbox")
    void queueFailureIsContained() {
        doThrow(new RuntimeException("queue down")).when(queue).send(any(), any());

        assertThat(service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values()))
                .hasSize(2);
    }

    @Test
    @DisplayName("An invitation has no employee: email only, to the recipient_email given")
    void invitationIsEmailOnly() {
        service.compose(
                NotificationEvent.USER_INVITATION,
                null,
                Map.of(
                        NotificationService.RECIPIENT_EMAIL,
                        "new.hire@globex.test",
                        "tenant_name",
                        "Globex",
                        "link",
                        "https://x"));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(saved.get(0).getRecipientEmployeeId()).isNull();
        assertThat(saved.get(0).getRecipientEmail()).isEqualTo("new.hire@globex.test");
    }

    @Test
    @DisplayName("No template for an event on a channel is an error, and nothing is written")
    void missingTemplateIsAnError() {
        assertThatThrownBy(() -> service.compose(NotificationEvent.PAYSLIP_READY, employeeId, values()))
                .isInstanceOf(NotificationService.TemplateMissingException.class);
        verify(notifications, never()).saveAll(anyIterable());
        verify(queue, never()).send(any(), any());
    }

    @Test
    @DisplayName("A missing value writes nothing - not even the channel that rendered")
    void missingValueWritesNothing() {
        assertThatThrownBy(() ->
                        service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, Map.of("leave_type", "Casual")))
                .isInstanceOf(TemplateRenderer.MissingValueException.class);
        verify(notifications, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("No recipient at all, or an employee of another tenant, is refused")
    void recipientIsRequiredAndInTheTenant() {
        assertThatThrownBy(() -> service.compose(NotificationEvent.LEAVE_APPROVED, null, values()))
                .isInstanceOf(NotificationService.ValidationException.class);
        assertThatThrownBy(() -> service.compose(NotificationEvent.LEAVE_APPROVED, UUID.randomUUID(), values()))
                .isInstanceOfSatisfying(NotificationService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("recipientEmployeeId"));
    }

    @Test
    @DisplayName("Linked to no employee (before W-13.4) the inbox is empty and nothing can be marked read")
    void unlinkedCallerSeesNothing() {
        when(recipients.currentEmployeeId()).thenReturn(Optional.empty());

        assertThat(service.mine(false, PageRequest.of(0, 20))).isEmpty();
        assertThatThrownBy(() -> service.markRead(UUID.randomUUID()))
                .isInstanceOf(NotificationService.NotFoundException.class);
        verify(notifications, never()).findByTenantIdAndRecipientEmployeeIdAndChannel(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Someone else's notification is not found when marked read")
    void anotherRecipientsNotificationIsNotFound() {
        when(recipients.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        UUID theirs = UUID.randomUUID();
        when(notifications.findByIdAndTenantIdAndRecipientEmployeeId(theirs, TENANT, employeeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(theirs)).isInstanceOf(NotificationService.NotFoundException.class);
    }

    private Map<String, Object> values() {
        return Map.of("employee_name", "Asha", "leave_type", "Casual <leave>");
    }

    private void template(NotificationEvent event, Channel channel, String subject, String body) {
        NotificationTemplate template =
                new NotificationTemplate(TENANT, event, channel, LocalDate.of(2026, 1, 1), "seed");
        template.apply(subject, body, true, "seed");
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        when(templates
                        .findFirstByTenantIdAndEventAndChannelAndLocaleAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                TENANT, event, channel, "en", LocalDate.of(2026, 9, 25)))
                .thenReturn(Optional.of(template));
    }
}
