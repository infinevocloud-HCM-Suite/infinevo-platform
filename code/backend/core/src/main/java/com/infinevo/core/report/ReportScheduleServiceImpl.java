package com.infinevo.core.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.UserAccountIdResolver;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule about a report schedule (W-23.2).
 *
 * <ul>
 *   <li>The definition exists in the tenant, and the caller holds its {@code required_action} — the
 *       check that stands for every later run, which has no caller.
 *   <li>A cadence the evaluator knows, and a {@code day_of_period} that can occur under it: none for
 *       {@code DAILY}, ISO 1–7 for {@code WEEKLY}, 1–31 for {@code MONTHLY} (a 31st falls back to the
 *       month's last day).
 *   <li>Filters the definition's source declares, so a schedule never fails at 02:00 for a typo
 *       made at 14:00.
 *   <li>At least one and at most {@value ReportScheduleService#MAX_RECIPIENTS} well-formed addresses.
 *       Addresses outside the tenant are allowed (decision 1); the entity is audited, so each one is
 *       on record.
 * </ul>
 */
@Service
public class ReportScheduleServiceImpl implements ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleServiceImpl.class);

    private static final Pattern RECIPIENT_SEPARATORS = Pattern.compile("[,;\\s]+");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Set<String> CADENCES =
            Set.of(ReportSchedule.DAILY, ReportSchedule.WEEKLY, ReportSchedule.MONTHLY);
    private static final int MAX_EMAIL = 254;
    private static final int MAX_ACTOR = 100;

    private final ReportScheduleRepository schedules;
    private final ReportDefinitionRepository definitions;
    private final ReportSourceRegistry sources;
    private final PermissionService permissions;
    private final UserAccountIdResolver userAccounts;
    private final ObjectMapper objectMapper;

    public ReportScheduleServiceImpl(
            ReportScheduleRepository schedules,
            ReportDefinitionRepository definitions,
            ReportSourceRegistry sources,
            PermissionService permissions,
            UserAccountIdResolver userAccounts,
            ObjectMapper objectMapper) {
        this.schedules = Objects.requireNonNull(schedules, "schedules must not be null");
        this.definitions = Objects.requireNonNull(definitions, "definitions must not be null");
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.userAccounts = Objects.requireNonNull(userAccounts, "userAccounts must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportScheduleResponse> list() {
        UUID tenantId = TenantContext.require();
        return schedules.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReportScheduleResponse put(UUID id, ReportScheduleRequest request) {
        UUID tenantId = TenantContext.require();
        if (id == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("id", "id is required"));
        }
        if (request == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("request", "A request body is required"));
        }
        if (request.definitionId() == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("definitionId", "definitionId is required"));
        }
        ReportDefinition definition = definitions
                .findByIdAndTenantId(request.definitionId(), tenantId)
                .orElseThrow(() -> new ReportDefinitionService.ValidationException(
                        Map.of("definitionId", "No report definition " + request.definitionId() + " in this tenant")));

        // Before the shape of the request: whoever cannot run the export may not learn more about it.
        permissions.require(definition.getRequiredAction());

        Map<String, String> errors = new LinkedHashMap<>();
        String cadence =
                request.cadence() == null ? null : request.cadence().trim().toUpperCase(Locale.ROOT);
        if (cadence == null || !CADENCES.contains(cadence)) {
            errors.put("cadence", "cadence must be DAILY, WEEKLY or MONTHLY");
        } else {
            checkDayOfPeriod(cadence, request.dayOfPeriod(), errors);
        }
        if (request.sendAtLocalTime() == null) {
            errors.put("sendAtLocalTime", "sendAtLocalTime is required, as HH:mm in the tenant's own time");
        }
        List<String> recipients = recipients(request.recipientEmails(), errors);
        String filters = filters(definition, request.filters(), errors);
        if (!errors.isEmpty()) {
            throw new ReportDefinitionService.ValidationException(errors);
        }

        String actor = currentActor();
        UUID ownerUserAccountId = resolveOwnerUserAccountId(tenantId);
        Integer dayOfPeriod = ReportSchedule.DAILY.equals(cadence) ? null : request.dayOfPeriod();
        boolean active = request.isActive() == null || request.isActive();
        ReportSchedule schedule =
                schedules.findByIdAndTenantId(id, tenantId).orElseGet(() -> new ReportSchedule(id, tenantId, actor));
        schedule.apply(
                definition.getId(),
                ownerUserAccountId,
                cadence,
                dayOfPeriod,
                request.sendAtLocalTime(),
                filters,
                String.join(", ", recipients),
                active,
                actor);
        ReportSchedule saved = schedules.saveAndFlush(schedule);
        log.info(
                "Saved report schedule {} for definition {} ({}, {} recipient(s)) in tenant {}",
                saved.getId(),
                definition.getCode(),
                cadence,
                recipients.size(),
                tenantId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        ReportSchedule schedule = schedules
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ReportDefinitionService.NotFoundException(id));
        schedules.delete(schedule);
        log.info("Deleted report schedule {} in tenant {}", id, tenantId);
    }

    /** The stored recipient list, split. Shared with the evaluator, so both read it one way. */
    public static List<String> recipientsOf(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(RECIPIENT_SEPARATORS.split(stored.trim()))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void checkDayOfPeriod(String cadence, Integer day, Map<String, String> errors) {
        if (ReportSchedule.WEEKLY.equals(cadence) && (day == null || day < 1 || day > 7)) {
            errors.put("dayOfPeriod", "a WEEKLY schedule needs dayOfPeriod 1 (Monday) to 7 (Sunday)");
        } else if (ReportSchedule.MONTHLY.equals(cadence) && (day == null || day < 1 || day > 31)) {
            errors.put(
                    "dayOfPeriod",
                    "a MONTHLY schedule needs dayOfPeriod 1 to 31; a 31st runs on shorter" + " months' last day");
        }
    }

    private static List<String> recipients(String raw, Map<String, String> errors) {
        Set<String> unique = new LinkedHashSet<>();
        for (String address : recipientsOf(raw)) {
            unique.add(address.toLowerCase(Locale.ROOT));
        }
        if (unique.isEmpty()) {
            errors.put("recipientEmails", "at least one recipient address is required");
            return List.of();
        }
        if (unique.size() > MAX_RECIPIENTS) {
            errors.put("recipientEmails", "at most " + MAX_RECIPIENTS + " recipients");
            return List.of();
        }
        List<String> invalid = unique.stream()
                .filter(a -> a.length() > MAX_EMAIL || !EMAIL.matcher(a).matches())
                .toList();
        if (!invalid.isEmpty()) {
            errors.put("recipientEmails", "not an email address: " + invalid);
        }
        return List.copyOf(unique);
    }

    private String filters(ReportDefinition definition, Map<String, String> requested, Map<String, String> errors) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        Set<String> declared = sources.find(definition.getSource())
                .map(ReportSource::filterNames)
                .orElse(Set.of());
        Set<String> unknown = new TreeSet<>(requested.keySet());
        unknown.removeAll(declared);
        if (!unknown.isEmpty()) {
            errors.put(
                    "filters",
                    "source " + definition.getSource() + " takes no filter " + unknown + "; it takes "
                            + new TreeSet<>(declared));
            return null;
        }
        try {
            return objectMapper.writeValueAsString(requested);
        } catch (JsonProcessingException e) {
            errors.put("filters", "filters must be an object of names to text values");
            return null;
        }
    }

    private ReportScheduleResponse toResponse(ReportSchedule s) {
        return new ReportScheduleResponse(
                s.getId(),
                s.getDefinitionId(),
                s.getCadence(),
                s.getDayOfPeriod(),
                s.getSendAtLocalTime(),
                parseFilters(s.getFilters()),
                recipientsOf(s.getRecipientEmails()),
                s.isActive(),
                s.getLastRunAt(),
                s.getLastRunStatus(),
                s.getLastDocumentId());
    }

    private Map<String, String> parseFilters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Report schedule filters are not a JSON object of strings; showing none");
            return Map.of();
        }
    }

    /**
     * The caller's {@code core.user_account.id}, stored as the schedule's owner (manager's review
     * item B-4) so the worker — which has nobody signed in — can re-check their {@code
     * required_action} before every run, instead of trusting the check made here once, at save time.
     *
     * <p>{@code null} for a caller with no JWT subject (a test, a job runner acting as "system"): the
     * evaluator refuses to run such a schedule rather than skip the check.
     */
    private UUID resolveOwnerUserAccountId(UUID tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String subject = auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : auth.getName();
        UUID keycloakUserId;
        try {
            keycloakUserId = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        return userAccounts.userAccountIdOf(tenantId, keycloakUserId).orElse(null);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ReportSchedule.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
