package com.infinevo.core.notification;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule about editing a template (W-20.1).
 *
 * <ul>
 *   <li>A template uses only its event's placeholders. One it cannot fill would fail every notification
 *       for that event from the moment it is saved; refusing it here is the cheaper place.
 *   <li>A {@code ${} that is not a well-formed placeholder is refused: it would reach people as text.
 *   <li>An email template has a subject.
 *   <li>An edit is a new version from today, never an overwrite of an earlier one.
 * </ul>
 */
@Service
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    static final int MAX_SUBJECT = 255;
    static final int MAX_BODY = 20_000;
    private static final int MAX_ACTOR = 100;

    private final NotificationTemplateRepository templates;
    private final Clock clock;

    @Autowired
    public NotificationTemplateServiceImpl(NotificationTemplateRepository templates) {
        this(templates, Clock.systemUTC());
    }

    NotificationTemplateServiceImpl(NotificationTemplateRepository templates, Clock clock) {
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> list(NotificationEvent event) {
        UUID tenantId = TenantContext.require();
        List<NotificationTemplate> rows = event == null
                ? templates.findByTenantIdOrderByEventAscChannelAscEffectiveFromDesc(tenantId)
                : templates.findByTenantIdAndEventOrderByChannelAscEffectiveFromDesc(tenantId, event);
        return rows.stream().map(NotificationTemplateResponse::from).toList();
    }

    @Override
    @Transactional
    public NotificationTemplateResponse put(NotificationEvent event, NotificationTemplateRequest request) {
        UUID tenantId = TenantContext.require();
        if (event == null) {
            throw new NotificationService.ValidationException(Map.of("event", "event is required"));
        }
        if (request == null) {
            throw new NotificationService.ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Channel channel = request.channel();
        if (channel == null) {
            errors.put("channel", "channel is required: IN_APP or EMAIL");
        }
        String body = trimToNull(request.body());
        if (body == null) {
            errors.put("body", "body is required");
        } else if (body.length() > MAX_BODY) {
            errors.put("body", "body must be at most " + MAX_BODY + " characters");
        }
        String subject = channel == Channel.EMAIL ? trimToNull(request.subject()) : null;
        if (channel == Channel.EMAIL && subject == null) {
            errors.put("subject", "an email template needs a subject");
        } else if (subject != null && subject.length() > MAX_SUBJECT) {
            errors.put("subject", "subject must be at most " + MAX_SUBJECT + " characters");
        }
        checkPlaceholders(event, "body", body, errors);
        checkPlaceholders(event, "subject", subject, errors);
        if (!errors.isEmpty()) {
            throw new NotificationService.ValidationException(errors);
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        String actor = currentActor();
        NotificationTemplate template = templates
                .findByTenantIdAndEventAndChannelAndLocaleAndEffectiveFrom(
                        tenantId, event, channel, NotificationTemplate.DEFAULT_LOCALE, today)
                .orElseGet(() -> new NotificationTemplate(tenantId, event, channel, today, actor));
        template.apply(subject, body, request.active() == null || request.active(), actor);
        return NotificationTemplateResponse.from(templates.saveAndFlush(template));
    }

    private static void checkPlaceholders(
            NotificationEvent event, String field, String template, Map<String, String> errors) {
        if (template == null) {
            return;
        }
        if (TemplateRenderer.hasMalformedPlaceholder(template)) {
            errors.put(
                    field,
                    field + " holds a ${ that is not a placeholder; use ${name} with lowercase letters,"
                            + " digits and underscores");
            return;
        }
        Set<String> unknown = new TreeSet<>(TemplateRenderer.placeholdersIn(template));
        unknown.removeAll(event.placeholders());
        if (!unknown.isEmpty()) {
            errors.put(
                    field,
                    event + " supplies no value for " + unknown + "; it supplies "
                            + new TreeSet<>(event.placeholders()));
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return NotificationTemplate.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
