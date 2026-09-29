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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(NotificationServiceImpl.class);
    private ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void setUp() {
        logged = new ListAppender<>();
        logged.start();
        serviceLogger.addAppender(logged);
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
        serviceLogger.detachAppender(logged);
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
    @DisplayName("With no queue producer configured the email is written and stays QUEUED")
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
    @DisplayName("compose joins the caller's transaction, so a rolled-back caller leaves no notification behind")
    void composeJoinsTheCallersTransaction() throws Exception {
        Transactional tx = NotificationServiceImpl.class
                .getMethod("compose", NotificationEvent.class, UUID.class, Map.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation())
                .as("REQUIRES_NEW would commit and queue the notification even when the caller rolls back")
                .isEqualTo(Propagation.REQUIRED);
    }

    @Test
    @DisplayName("No template in force: an error is logged, that channel is skipped, nothing is thrown at the caller")
    void missingTemplateIsLoggedNotThrown() {
        List<UUID> ids = service.compose(NotificationEvent.PAYSLIP_READY, employeeId, values());

        assertThat(ids).isEmpty();
        verify(notifications, never()).saveAll(anyIterable());
        verify(queue, never()).send(any(), any());
        assertThat(errors())
                .hasSize(2)
                .allMatch(message -> message.contains("PAYSLIP_READY") && message.contains("no template is in force"));
    }

    @Test
    @DisplayName(
            "A missing value skips only the channel that needs it, logs the placeholder's name, and throws nothing")
    void missingValueSkipsThatChannel() {
        // The in-app template uses only leave_type; the email also needs employee_name.
        List<UUID> ids =
                service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, Map.of("leave_type", "Casual <secret>"));

        assertThat(ids).hasSize(1);
        assertThat(saved).extracting(Notification::getChannel).containsExactly(Channel.IN_APP);
        verify(queue, never()).send(any(), any());
        assertThat(errors()).singleElement().satisfies(message -> assertThat(message)
                .contains("EMAIL")
                .contains("employee_name")
                .doesNotContain("secret"));
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
    @DisplayName("Linked to no employee, the inbox is empty and nothing can be marked read")
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

    @Test
    @DisplayName(
            "An inactive template switches the channel off: newer inactive template stops delivery without falling back to older active default")
    void inactiveTemplateSwitchesChannelOff() {
        NotificationTemplate inactiveEmail = new NotificationTemplate(
                TENANT, NotificationEvent.LEAVE_APPROVED, Channel.EMAIL, LocalDate.of(2026, 9, 25), "admin");
        inactiveEmail.apply("Subject", "<p>Inactive</p>", false, "admin");
        when(templates
                        .findFirstByTenantIdAndEventAndChannelAndLocaleAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                TENANT,
                                NotificationEvent.LEAVE_APPROVED,
                                Channel.EMAIL,
                                "en",
                                LocalDate.of(2026, 9, 25)))
                .thenReturn(Optional.of(inactiveEmail));

        List<UUID> ids = service.compose(NotificationEvent.LEAVE_APPROVED, employeeId, values());

        assertThat(ids).hasSize(1);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChannel()).isEqualTo(Channel.IN_APP);
        verify(queue, never()).send(any(), any());
    }

    @Test
    @DisplayName(
            "Invalid legacy work_email on employee does not fail compose: email channel is skipped and in-app is delivered")
    void invalidLegacyWorkEmailSkipsEmailChannel() {
        UUID legacyId = UUID.randomUUID();
        Employee legacyEmployee = mock(Employee.class);
        when(legacyEmployee.getId()).thenReturn(legacyId);
        when(legacyEmployee.getWorkEmail()).thenReturn("invalid-not-an-email");
        when(employees.findByIdAndTenantIdAndDeletedFalse(legacyId, TENANT)).thenReturn(Optional.of(legacyEmployee));

        List<UUID> ids = service.compose(NotificationEvent.LEAVE_APPROVED, legacyId, values());

        assertThat(ids).hasSize(1);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChannel()).isEqualTo(Channel.IN_APP);
        verify(queue, never()).send(any(), any());
        assertThat(logged.list)
                .as("the address is personal data and stays out of the log")
                .noneMatch(e -> e.getFormattedMessage().contains("invalid-not-an-email"));
    }

    private List<String> errors() {
        return logged.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
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
                        .findFirstByTenantIdAndEventAndChannelAndLocaleAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                TENANT, event, channel, "en", LocalDate.of(2026, 9, 25)))
                .thenReturn(Optional.of(template));
    }
}
