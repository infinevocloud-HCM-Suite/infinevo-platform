package com.infinevo.core.report;

import com.infinevo.core.authz.ActionRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule about a report definition (W-23.1, spec sections 4 and 6).
 *
 * <ul>
 *   <li>{@code source} names a registered {@link ReportSource}. A code with no bean is refused: a
 *       definition that can never run is a trap for the next person to find.
 *   <li>{@code columns} is non-empty, has no repeats, and every name is in that source's allow-list.
 *       This is the rule that keeps a definition from ever becoming a query.
 *   <li>{@code default_filters} names only filters the source declares.
 *   <li>{@code required_action} is a real action in {@code reference.action}; the foreign key in
 *       {@code V040} would refuse it anyway, and this turns the constraint error into a sentence.
 *   <li>A system definition cannot be edited ({@code 409}), as a system role cannot.
 * </ul>
 */
@Service
public class ReportDefinitionServiceImpl implements ReportDefinitionService {

    static final int MAX_CODE = 64;
    static final int MAX_NAME = 128;
    private static final int MAX_ACTOR = 100;

    /** Lowercase kebab, the form of the three seeded codes. */
    private static final Pattern CODE_FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final String CODE_INDEX = "uk_report_definition_tenant_code";

    private final ReportDefinitionRepository definitions;
    private final ReportSourceRegistry sources;
    private final ActionRepository actions;
    private final PermissionService permissions;

    public ReportDefinitionServiceImpl(
            ReportDefinitionRepository definitions,
            ReportSourceRegistry sources,
            ActionRepository actions,
            PermissionService permissions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions must not be null");
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.actions = Objects.requireNonNull(actions, "actions must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
    }

    /**
     * Only the definitions the caller could run. A definition over salary data must not even be
     * listed to someone who cannot see salary data (spec section 4: "hides the definition").
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReportDefinitionResponse> list() {
        UUID tenantId = TenantContext.require();
        return definitions.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .filter(definition -> permissions.holds(definition.getRequiredAction()))
                .map(ReportDefinitionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ReportDefinitionResponse create(ReportDefinitionRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String code = trimToNull(request.code());
        if (code == null) {
            errors.put("code", "code is required");
        } else if (code.length() > MAX_CODE || !CODE_FORMAT.matcher(code).matches()) {
            errors.put(
                    "code",
                    "code must be lowercase letters and digits separated by single hyphens, at most " + MAX_CODE
                            + " characters");
        }
        Cleaned cleaned = validate(request, errors);

        if (definitions.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateCodeException(code);
        }
        ReportDefinition definition = new ReportDefinition(tenantId, code, currentActor());
        cleaned.applyTo(definition, currentActor());
        try {
            return ReportDefinitionResponse.from(definitions.saveAndFlush(definition));
        } catch (DataIntegrityViolationException e) {
            if (namesIndex(e, CODE_INDEX)) {
                throw new DuplicateCodeException(code);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ReportDefinitionResponse update(UUID id, ReportDefinitionRequest request) {
        UUID tenantId = TenantContext.require();
        ReportDefinition definition =
                definitions.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));
        if (definition.isSystem()) {
            throw new SystemDefinitionException(definition.getCode());
        }
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Cleaned cleaned = validate(request, new LinkedHashMap<>());
        cleaned.applyTo(definition, currentActor());
        return ReportDefinitionResponse.from(definitions.saveAndFlush(definition));
    }

    /** Checks every field but the code and returns the cleaned values, or throws with all the errors. */
    private Cleaned validate(ReportDefinitionRequest request, Map<String, String> errors) {
        String name = trimToNull(request.name());
        if (name == null) {
            errors.put("name", "name is required");
        } else if (name.length() > MAX_NAME) {
            errors.put("name", "name must be at most " + MAX_NAME + " characters");
        }
        if (request.format() == null) {
            errors.put("format", "format is required: CSV or XLSX");
        }

        ReportSource source = null;
        String sourceCode = trimToNull(request.source());
        if (sourceCode == null) {
            errors.put("source", "source is required");
        } else {
            source = sources.find(sourceCode).orElse(null);
            if (source == null) {
                errors.put("source", "Unknown source " + sourceCode + "; known sources are " + sources.codes());
            }
        }

        List<String> columns = request.columns() == null
                ? List.of()
                : request.columns().stream()
                        .map(ReportDefinitionServiceImpl::trimToNull)
                        .toList();
        if (columns.isEmpty() || columns.contains(null)) {
            errors.put("columns", "columns is required and must not contain a blank entry");
        } else if (new HashSet<>(columns).size() != columns.size()) {
            errors.put("columns", "columns must not repeat a column");
        } else if (source != null) {
            Set<String> allowed =
                    source.columns().stream().map(ReportColumn::name).collect(Collectors.toSet());
            List<String> unknown =
                    columns.stream().filter(c -> !allowed.contains(c)).toList();
            if (!unknown.isEmpty()) {
                errors.put("columns", "Not columns of source " + sourceCode + ": " + String.join(", ", unknown));
            }
        }

        Map<String, String> defaults = request.defaultFilters() == null ? Map.of() : request.defaultFilters();
        if (source != null) {
            Set<String> declared = source.filterNames();
            List<String> unknownFilters = defaults.keySet().stream()
                    .filter(f -> !declared.contains(f))
                    .sorted()
                    .toList();
            if (!unknownFilters.isEmpty()) {
                errors.put("defaultFilters", "Source " + sourceCode + " takes no filter " + unknownFilters);
            }
        }

        String requiredAction = trimToNull(request.requiredAction());
        if (requiredAction == null) {
            errors.put("requiredAction", "requiredAction is required");
        } else if (!actions.existsById(requiredAction)) {
            errors.put("requiredAction", "Unknown action " + requiredAction);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Cleaned(name, sourceCode, columns, defaults, request.format(), requiredAction);
    }

    private record Cleaned(
            String name,
            String source,
            List<String> columns,
            Map<String, String> defaultFilters,
            ExportFormat format,
            String requiredAction) {

        void applyTo(ReportDefinition definition, String actor) {
            definition.apply(name, source, columns, defaultFilters, format, requiredAction, actor);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static boolean namesIndex(Throwable e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(indexName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ReportDefinition.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
