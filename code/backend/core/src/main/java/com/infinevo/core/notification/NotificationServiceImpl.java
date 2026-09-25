package com.infinevo.core.notification;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.logging.MdcLoggingContext;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Composes notifications and serves the in-app inbox (W-20.1, spec sections 3 and 4).
 *
 * <p><strong>The row is the outbox; the queue message is a nudge.</strong> An email notification is
 * written {@code QUEUED}, and only after the transaction commits is its id put on the
 * {@value NotificationService#QUEUE} queue — so a rolled-back request queues nothing, and a queue that
 * is down loses nothing: {@code W-20.2}'s sweep sends whatever is still {@code QUEUED}
 * ({@code idx_notification_tenant_status_queued}). Storage Queue guarantees no ordering and may deliver
 * twice ({@code D-50}), so the worker must treat the id as idempotent, which a status column makes easy.
 *
 * <p><strong>The producer may be absent.</strong> Until {@code W-52.1} moves the queue configuration
 * into {@code shared}, {@code app} has no {@link QueueProducer} bean. Then the email stays
 * {@code QUEUED} for the sweep, exactly as if the queue were down — nothing here changes when it lands.
 *
 * <p><strong>The recipient is looked up in the bound tenant</strong>, for the reason
 * {@code DocumentServiceImpl} gives: the foreign key is checked as the table owner and would accept an
 * employee of another tenant.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final int MAX_EMAIL = 255;
    private static final int MAX_SUBJECT_REF = 128;
    private static final int MAX_ACTOR = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final NotificationTemplateRepository templates;
    private final EmployeeRepository employees;
    private final TemplateRenderer renderer;
    private final NotificationRecipientResolver recipients;
    private final Supplier<QueueProducer> producer;
    private final Clock clock;

    @Autowired
    public NotificationServiceImpl(
            NotificationRepository notifications,
            NotificationTemplateRepository templates,
            EmployeeRepository employees,
            TemplateRenderer renderer,
            NotificationRecipientResolver recipients,
            ObjectProvider<QueueProducer> producers) {
        // getIfUnique, not getIfAvailable: the lookup runs after commit, where a second producer bean
        // would throw into a request whose rows are already durable. Null instead - the sweep covers it.
        this(notifications, templates, employees, renderer, recipients, producers::getIfUnique, Clock.systemUTC());
    }

    NotificationServiceImpl(
            NotificationRepository notifications,
            NotificationTemplateRepository templates,
            EmployeeRepository employees,
            TemplateRenderer renderer,
            NotificationRecipientResolver recipients,
            Supplier<QueueProducer> producer,
            Clock clock) {
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.recipients = Objects.requireNonNull(recipients, "recipients must not be null");
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public List<UUID> compose(NotificationEvent event, UUID recipientEmployeeId, Map<String, Object> data) {
        UUID tenantId = TenantContext.require();
        if (event == null) {
            throw new ValidationException(Map.of("event", "event is required"));
        }
        Map<String, Object> values = data == null ? Map.of() : data;

        Employee employee = recipientEmployeeId == null
                ? null
                : employees
                        .findByIdAndTenantIdAndDeletedFalse(recipientEmployeeId, tenantId)
                        .orElseThrow(() -> new ValidationException(Map.of(
                                "recipientEmployeeId", "No employee " + recipientEmployeeId + " in this tenant")));
        String email = email(values, employee);
        if (employee == null && email == null) {
            throw new ValidationException(
                    Map.of("recipient", "A notification needs a recipient employee or a " + RECIPIENT_EMAIL));
        }
        String subjectRef = subjectRef(values);

        List<Channel> channels = new ArrayList<>();
        if (employee != null) {
            channels.add(Channel.IN_APP);
        }
        if (email != null) {
            channels.add(Channel.EMAIL);
        }

        // Render every channel before writing any, so a missing value writes nothing at all.
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<Notification> rendered = new ArrayList<>();
        Instant now = clock.instant();
        String actor = currentActor();
        for (Channel channel : channels) {
            NotificationTemplate template = templates
                    .findFirstByTenantIdAndEventAndChannelAndLocaleAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            tenantId, event, channel, NotificationTemplate.DEFAULT_LOCALE, today)
                    .orElseThrow(() -> new TemplateMissingException(event, channel));
            String subject =
                    template.getSubject() == null ? null : renderer.renderSubject(template.getSubject(), values);
            String body = renderer.render(template.getBody(), values, channel == Channel.EMAIL);
            rendered.add(new Notification(
                    tenantId,
                    employee == null ? null : employee.getId(),
                    channel == Channel.EMAIL ? email : null,
                    event,
                    channel,
                    subject,
                    body,
                    // An in-app notification is delivered by being stored; an email waits for W-20.2.
                    channel == Channel.IN_APP ? NotificationStatus.SENT : NotificationStatus.QUEUED,
                    template.getId(),
                    now,
                    subjectRef,
                    actor));
        }

        List<UUID> ids = new ArrayList<>();
        List<UUID> emails = new ArrayList<>();
        for (Notification notification : notifications.saveAll(rendered)) {
            ids.add(notification.getId());
            if (notification.getChannel() == Channel.EMAIL) {
                emails.add(notification.getId());
            }
        }
        enqueueAfterCommit(tenantId, emails);
        log.info("Composed {} for {} channel(s) in tenant {}", event, ids.size(), tenantId);
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> mine(boolean unreadOnly, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Pageable page = newestFirst(pageable);
        return recipients
                .currentEmployeeId()
                .map(employeeId -> (unreadOnly
                                ? notifications.findByTenantIdAndRecipientEmployeeIdAndChannelAndReadAtIsNull(
                                        tenantId, employeeId, Channel.IN_APP, page)
                                : notifications.findByTenantIdAndRecipientEmployeeIdAndChannel(
                                        tenantId, employeeId, Channel.IN_APP, page))
                        .map(NotificationResponse::from))
                .orElseGet(() -> Page.empty(page));
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID id) {
        UUID tenantId = TenantContext.require();
        UUID employeeId = recipients.currentEmployeeId().orElseThrow(() -> new NotFoundException(id));
        Notification notification = notifications
                .findByIdAndTenantIdAndRecipientEmployeeId(id, tenantId, employeeId)
                .orElseThrow(() -> new NotFoundException(id));
        notification.markRead(clock.instant(), currentActor());
        return NotificationResponse.from(notifications.save(notification));
    }

    /**
     * Puts each email notification's id on the queue once the transaction commits. With no transaction
     * the rows are already committed, so now.
     */
    private void enqueueAfterCommit(UUID tenantId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String correlationId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        Runnable send = () -> enqueue(tenantId, ids, correlationId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** Never throws: the rows are committed, and a lost nudge is what the sweep exists for. */
    private void enqueue(UUID tenantId, List<UUID> ids, String correlationId) {
        QueueProducer queue = producer.get();
        if (queue == null) {
            log.debug(
                    "No queue producer in this runtime; {} email notification(s) stay QUEUED for the delivery sweep",
                    ids.size());
            return;
        }
        for (UUID id : ids) {
            try {
                queue.send(QUEUE, QueueMessage.of(id.toString(), tenantId, QUEUE, correlationId, id.toString()));
            } catch (RuntimeException e) {
                log.warn("Could not queue notification {}; it stays QUEUED for the delivery sweep", id, e);
            }
        }
    }

    private static String email(Map<String, Object> values, Employee employee) {
        Object given = values.get(RECIPIENT_EMAIL);
        String email = given == null ? null : given.toString().trim();
        if (email == null || email.isEmpty()) {
            email = employee == null ? null : employee.getWorkEmail();
        }
        if (email == null || email.isBlank()) {
            return null;
        }
        if (email.length() > MAX_EMAIL || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ValidationException(Map.of(RECIPIENT_EMAIL, "Not an email address: " + email));
        }
        return email;
    }

    private static String subjectRef(Map<String, Object> values) {
        Object ref = values.get(SUBJECT_REF);
        if (ref == null) {
            return null;
        }
        String text = ref.toString();
        if (text.length() > MAX_SUBJECT_REF) {
            throw new ValidationException(Map.of(SUBJECT_REF, "subject_ref must be at most " + MAX_SUBJECT_REF));
        }
        return text;
    }

    private static Pageable newestFirst(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, Sort.by(Sort.Direction.DESC, "queuedAt"));
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return Notification.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
