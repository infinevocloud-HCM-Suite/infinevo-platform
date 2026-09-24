package com.infinevo.core.authz;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Every rule about roles and grants (W-11.1, spec sections 4 and 7).
 *
 * <ul>
 *   <li>A role holds only actions {@code reference.action} defines — an unknown code is a
 *       {@code 400} naming every unknown code, not only the first. The foreign key in {@code V022}
 *       would refuse it anyway; the check turns a constraint error into a sentence.
 *   <li>A system role — one of the seven {@code V022} seeds — cannot be renamed, re-actioned or
 *       deleted ({@code 409}).
 *   <li>A role any user holds cannot be deleted ({@code 409}).
 *   <li>A code is unique within the tenant ({@code 409}), checked here and enforced by
 *       {@code idx_role_tenant_code}: the check gives the message, the index closes the race.
 *   <li>A grant names roles and a user of the bound tenant only. A foreign tenant's role is not found
 *       under row-level security ({@code 404}), and the composite key in {@code V023} refuses it
 *       besides. The user side has no such key, so the account is looked up here before anything is
 *       written.
 * </ul>
 *
 * <p>The tenant is read once per call from {@link TenantContext}, never from a parameter.
 *
 * <p>Action sets and grants are replaced by difference, not by delete-all-then-insert: only the rows
 * that actually change are written, so {@code created_at} / {@code created_by} on an unchanged grant
 * keep saying when it was really made. Hibernate flushes inserts before deletes, which is safe here
 * precisely because the added and removed sets are disjoint.
 *
 * <p><strong>Every successful write bumps the tenant's permission version, after it commits</strong>
 * (W-11.2, spec section 4). Create, update, delete and grant each end in {@link #bumpAfterCommit}, which
 * registers a {@link TransactionSynchronization} whose {@code afterCommit} calls
 * {@link PermissionCache#bumpVersion}. After commit, not before: a bump before commit would let another
 * replica reload the old rows under the new version and keep them for the whole TTL. A write that is
 * refused or rolls back registers nothing, or never reaches {@code afterCommit}, so it bumps nothing. The
 * bump lives here in the service, not in a controller — the frozen design invalidated from a controller
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/RoleActionController.java:94}) and reached only the
 * replica that served the call.
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    static final int MAX_CODE = 64;
    static final int MAX_NAME = 128;

    /** The audit columns are {@code varchar(100)} in every migration. */
    private static final int MAX_ACTOR = 100;

    /** Lowercase kebab — the form of the seven system codes ({@code hr}, {@code payroll-officer}). */
    private static final Pattern CODE_FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    /** The unique index from {@code V021__role.sql}. Rename it there, rename it here. */
    private static final String CODE_INDEX = "idx_role_tenant_code";

    /** The seeded role holding {@code core.tenant.provision} — V022. Never granted through this API. */
    private static final String PLATFORM_ADMIN = "platform-admin";

    private final RoleRepository roleRepository;
    private final RoleActionRepository roleActionRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActionRepository actionRepository;
    private final UserAccountRepository userAccountRepository;
    private final PermissionCache permissionCache;

    public RoleServiceImpl(
            RoleRepository roleRepository,
            RoleActionRepository roleActionRepository,
            UserRoleRepository userRoleRepository,
            ActionRepository actionRepository,
            UserAccountRepository userAccountRepository,
            PermissionCache permissionCache) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleActionRepository =
                Objects.requireNonNull(roleActionRepository, "roleActionRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository must not be null");
        this.userAccountRepository =
                Objects.requireNonNull(userAccountRepository, "userAccountRepository must not be null");
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionResponse> listActions() {
        return actionRepository.findAllByOrderByCodeAsc().stream()
                .map(ActionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String name = required(errors, "name", request.name(), MAX_NAME);
        String code = resolveCode(errors, request.code(), name);
        Set<String> actionCodes = cleanActionCodes(errors, request.actionCodes());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        requireKnownActions(actionCodes);

        if (roleRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateCodeException(code);
        }

        String actor = currentActor();
        Role role;
        try {
            role = roleRepository.saveAndFlush(new Role(tenantId, code, name, actor));
        } catch (DataIntegrityViolationException e) {
            // Only the (tenant_id, code) index means "duplicate". Anything else is rethrown as it is —
            // calling every integrity failure a duplicate was a review finding on W-13.1.
            if (namesIndex(e, CODE_INDEX)) {
                throw new DuplicateCodeException(code);
            }
            throw e;
        }
        List<RoleAction> held = new ArrayList<>();
        for (String actionCode : actionCodes) {
            held.add(new RoleAction(tenantId, role.getId(), actionCode, actor));
        }
        roleActionRepository.saveAll(held);
        bumpAfterCommit(tenantId);
        return RoleResponse.from(role, actionCodes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        UUID tenantId = TenantContext.require();
        List<Role> roles = roleRepository.findByTenantIdOrderByCodeAsc(tenantId);
        return withActions(tenantId, roles);
    }

    @Override
    @Transactional
    public RoleResponse update(UUID id, RoleUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        Role role = requireRole(id, tenantId);
        if (role.isSystem()) {
            throw new SystemRoleException(role.getCode(), "edited");
        }
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String name = required(errors, "name", request.name(), MAX_NAME);
        Set<String> wanted = cleanActionCodes(errors, request.actionCodes());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        requireKnownActions(wanted);

        String actor = currentActor();
        role.rename(name, actor);

        List<RoleAction> current = roleActionRepository.findByTenantIdAndRoleId(tenantId, role.getId());
        Set<String> have = current.stream().map(RoleAction::getActionCode).collect(Collectors.toSet());
        List<RoleAction> removed = current.stream()
                .filter(ra -> !wanted.contains(ra.getActionCode()))
                .toList();
        List<RoleAction> added = wanted.stream()
                .filter(code -> !have.contains(code))
                .map(code -> new RoleAction(tenantId, role.getId(), code, actor))
                .toList();
        roleActionRepository.deleteAll(removed);
        roleActionRepository.saveAll(added);
        roleRepository.save(role);
        bumpAfterCommit(tenantId);
        return RoleResponse.from(role, wanted);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Role role = requireRole(id, tenantId);
        if (role.isSystem()) {
            throw new SystemRoleException(role.getCode(), "deleted");
        }
        long holders = userRoleRepository.countByTenantIdAndRoleId(tenantId, role.getId());
        if (holders > 0) {
            throw new RoleInUseException(role.getCode(), holders);
        }

        // Flushed here so a grant made concurrently, between the count and the delete, surfaces as the
        // 409 it is rather than a foreign-key error at commit — the reasoning of
        // AbstractOrgMasterServiceImpl.delete.
        try {
            roleActionRepository.deleteAll(roleActionRepository.findByTenantIdAndRoleId(tenantId, role.getId()));
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RoleInUseException(role.getCode(), 1);
        }
        bumpAfterCommit(tenantId);
    }

    @Override
    @Transactional
    public UserRolesResponse replaceUserRoles(UUID userAccountId, UserRolesRequest request) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(userAccountId, "userAccountId must not be null");

        // core.user_role.user_account_id references user_account(id) alone (V023), so the database
        // would accept a grant to another tenant's user. Under row-level security that account is not
        // visible here; the tenant comparison says the same thing again without relying on it.
        boolean userVisible = userAccountRepository
                .findById(userAccountId)
                .filter(account -> tenantId.equals(account.getTenantId()))
                .isPresent();
        if (!userVisible) {
            throw new NotFoundException("No user " + userAccountId + " in this tenant");
        }

        if (request == null || request.roleIds() == null) {
            throw new ValidationException(Map.of("roleIds", "roleIds is required; send an empty list to revoke all"));
        }
        // A stream check, not contains(null): List.of(...) throws on contains(null) rather than answering.
        if (request.roleIds().stream().anyMatch(Objects::isNull)) {
            throw new ValidationException(Map.of("roleIds", "roleIds must not contain null"));
        }
        Set<UUID> wanted = new LinkedHashSet<>(request.roleIds());

        List<Role> roles = wanted.isEmpty() ? List.of() : roleRepository.findByTenantIdAndIdIn(tenantId, wanted);
        if (roles.size() != wanted.size()) {
            Set<UUID> found = roles.stream().map(Role::getId).collect(Collectors.toSet());
            String missing = wanted.stream()
                    .filter(roleId -> !found.contains(roleId))
                    .map(UUID::toString)
                    .collect(Collectors.joining(", "));
            throw new NotFoundException("No role " + missing + " in this tenant");
        }

        String actor = currentActor();
        List<UserRole> current = userRoleRepository.findByTenantIdAndUserAccountId(tenantId, userAccountId);
        Set<UUID> have = current.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<UserRole> removed =
                current.stream().filter(ur -> !wanted.contains(ur.getRoleId())).toList();
        List<UserRole> added = wanted.stream()
                .filter(roleId -> !have.contains(roleId))
                .map(roleId -> new UserRole(tenantId, userAccountId, roleId, actor))
                .toList();
        // platform-admin holds core.tenant.provision, which no customer may hold. Every tenant is seeded
        // with the role (spec §13 decision 2), so this API refuses to hand it out; an existing holder
        // keeps it. Platform staff are given it by provisioning, never from inside a tenant.
        Set<UUID> platformAdmin = roles.stream()
                .filter(role -> role.isSystem() && PLATFORM_ADMIN.equals(role.getCode()))
                .map(Role::getId)
                .collect(Collectors.toSet());
        if (added.stream().anyMatch(ur -> platformAdmin.contains(ur.getRoleId()))) {
            throw new SystemRoleException(PLATFORM_ADMIN, "granted from inside a tenant");
        }
        userRoleRepository.deleteAll(removed);
        userRoleRepository.saveAll(added);
        bumpAfterCommit(tenantId);

        List<Role> ordered = roles.stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .toList();
        return new UserRolesResponse(userAccountId, withActions(tenantId, ordered));
    }

    /**
     * Invalidates the tenant's cached permission sets on every replica once this transaction commits.
     *
     * <p>Called last in each write path, after every refusal has had its chance to throw, so a refused
     * write registers nothing; and a transaction that rolls back later never runs {@code afterCommit}.
     * Outside a transaction — no caller does this; every write method is {@code @Transactional} — the
     * writes have already been committed statement by statement, so the bump runs at once.
     */
    private void bumpAfterCommit(UUID tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bumpNow(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bumpNow(tenantId);
            }
        });
    }

    /**
     * The bump itself, which never throws.
     *
     * <p>By the time it runs the change is committed. An exception from {@code afterCommit} propagates
     * to the caller, so letting one escape would answer a committed change with a {@code 500} — and a
     * client that retries a grant it was told failed. Instead the failure is logged at {@code ERROR}:
     * the change stands, other replicas may serve the previous set until
     * {@link PermissionCache#PERMISSION_TTL} expires it, and that ten-minute TTL is the backstop.
     */
    private void bumpNow(UUID tenantId) {
        try {
            permissionCache.bumpVersion(tenantId);
        } catch (RuntimeException e) {
            log.error(
                    "Permission version bump failed for tenant {} after a committed role change; cached"
                            + " permission sets may be stale for up to {}",
                    tenantId,
                    PermissionCache.PERMISSION_TTL,
                    e);
        }
    }

    /** The role with this id in the bound tenant, or {@link NotFoundException}. The single read path. */
    private Role requireRole(UUID id, UUID tenantId) {
        return roleRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("No role " + id + " in this tenant"));
    }

    /** Repacks roles with their action codes — two queries however many roles there are. */
    private List<RoleResponse> withActions(UUID tenantId, List<Role> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = roles.stream().map(Role::getId).toList();
        Map<UUID, List<String>> byRole = new HashMap<>();
        for (RoleAction ra : roleActionRepository.findByTenantIdAndRoleIdIn(tenantId, ids)) {
            byRole.computeIfAbsent(ra.getRoleId(), k -> new ArrayList<>()).add(ra.getActionCode());
        }
        return roles.stream()
                .map(role -> RoleResponse.from(role, byRole.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    /**
     * Refuses any code {@code reference.action} does not hold, naming all of them.
     *
     * <p>This is the rule that stops a role inventing an action at runtime, as the frozen Payroll
     * allowed ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/action/ActionServiceImpl.java:24-34}).
     */
    private void requireKnownActions(Set<String> actionCodes) {
        if (actionCodes.isEmpty()) {
            return;
        }
        Set<String> known = actionRepository.findAllById(actionCodes).stream()
                .map(Action::getCode)
                .collect(Collectors.toSet());
        Set<String> unknown = new TreeSet<>(actionCodes);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new ValidationException(Map.of("actionCodes", "Unknown action codes: " + String.join(", ", unknown)));
        }
    }

    /** Trims, drops duplicates, keeps order. Null list and blank entries are errors. */
    static Set<String> cleanActionCodes(Map<String, String> errors, Collection<String> actionCodes) {
        if (actionCodes == null) {
            errors.put("actionCodes", "actionCodes is required; send an empty list for a role with no actions");
            return Set.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String code : actionCodes) {
            String trimmed = trimToNull(code);
            if (trimmed == null) {
                errors.put("actionCodes", "actionCodes must not contain a blank entry");
                return Set.of();
            }
            cleaned.add(trimmed);
        }
        return cleaned;
    }

    /**
     * The code as given, or one derived from the name.
     *
     * <p>Derived as lowercase kebab: every run of characters that is not a letter or digit becomes one
     * hyphen, and leading and trailing hyphens go. "Payroll Reviewer (EU)" gives
     * {@code payroll-reviewer-eu}. A name with no letter or digit in it gives nothing, and the caller
     * is asked for a code.
     */
    static String resolveCode(Map<String, String> errors, String requested, String name) {
        String code = trimToNull(requested);
        if (code == null) {
            if (name == null) {
                return null; // name is already reported missing
            }
            code = deriveCode(name);
            if (code.isEmpty()) {
                errors.put("code", "code is required when the name has no letters or digits to derive one from");
                return null;
            }
        }
        if (code.length() > MAX_CODE) {
            errors.put("code", "code must be at most " + MAX_CODE + " characters");
        } else if (!CODE_FORMAT.matcher(code).matches()) {
            errors.put("code", "code must be lowercase letters and digits separated by single hyphens");
        }
        return code;
    }

    static String deriveCode(String name) {
        String derived =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (derived.length() > MAX_CODE) {
            derived = derived.substring(0, MAX_CODE).replaceAll("-+$", "");
        }
        return derived;
    }

    private static String required(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            errors.put(field, field + " is required");
            return null;
        }
        if (trimmed.length() > max) {
            errors.put(field, field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Whether an index name appears anywhere in a throwable's cause chain. */
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

    /**
     * The authenticated subject, for {@code created_by} / {@code updated_by}; {@code system} when there
     * is none. Same rule as {@code EmployeeServiceImpl.currentActor}.
     */
    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return AuthzRow.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
