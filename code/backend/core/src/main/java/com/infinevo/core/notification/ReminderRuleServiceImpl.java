package com.infinevo.core.notification;

import com.infinevo.shared.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ReminderRuleService} (W-20.2).
 */
@Service
public class ReminderRuleServiceImpl implements ReminderRuleService {

    private static final Logger log = LoggerFactory.getLogger(ReminderRuleServiceImpl.class);

    private final ReminderRuleRepository repository;
    private final List<ReminderAudienceResolver> audienceResolvers;
    private final List<ReminderAnchorResolver> anchorResolvers;

    /** WEEKLY rules only: no anchor resolver is registered. */
    public ReminderRuleServiceImpl(
            ReminderRuleRepository repository, List<ReminderAudienceResolver> audienceResolvers) {
        this(repository, audienceResolvers, List.of());
    }

    @Autowired
    public ReminderRuleServiceImpl(
            ReminderRuleRepository repository,
            List<ReminderAudienceResolver> audienceResolvers,
            List<ReminderAnchorResolver> anchorResolvers) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.audienceResolvers = audienceResolvers != null ? audienceResolvers : List.of();
        this.anchorResolvers = anchorResolvers != null ? anchorResolvers : List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReminderRuleResponse> list(NotificationEvent event, Boolean isActive) {
        UUID tenantId = TenantContext.require();
        List<ReminderRule> rules;
        if (event != null && isActive != null) {
            rules = repository.findByTenantIdAndEventAndIsActive(tenantId, event, isActive);
        } else if (event != null) {
            rules = repository.findByTenantIdAndEvent(tenantId, event);
        } else if (isActive != null) {
            rules = repository.findByTenantIdAndIsActive(tenantId, isActive);
        } else {
            rules = repository.findByTenantId(tenantId);
        }
        return rules.stream().map(ReminderRuleResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReminderRuleResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        ReminderRule rule = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationService.NotFoundException(id));
        return ReminderRuleResponse.from(rule);
    }

    @Override
    @Transactional
    public ReminderRuleResponse create(ReminderRuleRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();
        validate(request);

        String actor = currentActor();
        ReminderRule rule = new ReminderRule(
                tenantId,
                request.event(),
                request.audience().trim(),
                request.anchor(),
                request.offsetDays(),
                request.dayOfWeek(),
                request.sendAtLocalTime(),
                request.repeatEveryDays(),
                request.maxRepeats(),
                actor);

        ReminderRule saved = repository.save(rule);
        log.info("Created reminder rule {} for event {} in tenant {}", saved.getId(), saved.getEvent(), tenantId);
        return ReminderRuleResponse.from(saved);
    }

    @Override
    @Transactional
    public ReminderRuleResponse update(UUID id, ReminderRuleRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();
        validate(request);

        ReminderRule rule = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationService.NotFoundException(id));

        rule.setEvent(request.event());
        rule.setAudience(request.audience().trim());
        rule.setAnchor(request.anchor());
        rule.setOffsetDays(request.offsetDays());
        rule.setDayOfWeek(request.dayOfWeek());
        rule.setSendAtLocalTime(request.sendAtLocalTime());
        rule.setRepeatEveryDays(request.repeatEveryDays());
        rule.setMaxRepeats(request.maxRepeats());
        rule.setUpdatedBy(currentActor());

        ReminderRule saved = repository.save(rule);
        log.info("Updated reminder rule {} for event {} in tenant {}", saved.getId(), saved.getEvent(), tenantId);
        return ReminderRuleResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        ReminderRule rule = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationService.NotFoundException(id));

        rule.setActive(false);
        rule.setUpdatedBy(currentActor());
        repository.save(rule);
        log.info("Soft-deleted reminder rule {} in tenant {}", id, tenantId);
    }

    private void validate(ReminderRuleRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // What a reminder can fill in is what the sweep supplies plus what the named audience supplies itself (W-43.1).
        Optional<ReminderAudienceResolver> audience =
                request.audience() == null || request.audience().isBlank()
                        ? Optional.empty()
                        : audienceResolvers.stream()
                                .filter(resolver -> resolver.audience()
                                        .equalsIgnoreCase(request.audience().trim()))
                                .findFirst();
        Set<String> supplied = new TreeSet<>(SUPPLIED_PLACEHOLDERS);
        audience.ifPresent(resolver -> supplied.addAll(resolver.suppliedPlaceholders()));

        if (request.event() == null) {
            fieldErrors.put("event", "event is required");
        } else if (!supplied.containsAll(request.event().placeholders())) {
            Set<String> missing = new TreeSet<>(request.event().placeholders());
            missing.removeAll(supplied);
            fieldErrors.put(
                    "event",
                    request.event() + " needs " + missing + ", which a reminder cannot supply; a reminder supplies "
                            + supplied);
        }

        if (request.audience() == null || request.audience().isBlank()) {
            fieldErrors.put("audience", "audience is required");
        } else if (audience.isEmpty()) {
            fieldErrors.put("audience", "Unknown audience: " + request.audience());
        }

        if (request.anchor() == null) {
            fieldErrors.put("anchor", "anchor is required");
        } else if (request.anchor() == Anchor.WEEKLY) {
            if (request.dayOfWeek() == null || request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
                fieldErrors.put("day_of_week", "day_of_week must be between 1 and 7 for WEEKLY rules");
            }
        } else if (anchorResolvers.stream().noneMatch(r -> r.anchor() == request.anchor())) {
            // Refused now rather than accepted and silently never run (the audience rule, spec section 4).
            fieldErrors.put(
                    "anchor",
                    "Nothing in this runtime supplies the " + request.anchor()
                            + " date yet; only WEEKLY rules can run");
        }

        if (request.offsetDays() == null) {
            fieldErrors.put("offset_days", "offset_days is required");
        }

        if (request.sendAtLocalTime() == null) {
            fieldErrors.put("send_at_local_time", "send_at_local_time is required");
        }

        if (request.repeatEveryDays() != null && request.repeatEveryDays() <= 0) {
            fieldErrors.put("repeat_every_days", "repeat_every_days must be positive");
        }

        if (request.maxRepeats() != null && request.maxRepeats() < 0) {
            fieldErrors.put("max_repeats", "max_repeats must be non-negative");
        }

        if (!fieldErrors.isEmpty()) {
            throw new NotificationService.ValidationException(fieldErrors);
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ReminderRule.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
