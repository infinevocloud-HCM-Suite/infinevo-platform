package com.infinevo.shared.authz;

import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Does the caller hold this action in the bound tenant? (W-11.2, spec section 3.)
 *
 * <pre>
 * tenant  - TenantContext, bound by TenantContextFilter; never a header or parameter
 * user    - the token's subject, the same UUID UserProfileSyncFilter syncs
 * actions - PermissionCache hit, or on a miss: user_account id -> ActionSource -> cache
 * </pre>
 *
 * <p><strong>Fails closed.</strong> Each of these refuses, with the reason logged, and none allows:
 * no tenant bound; no authenticated user, or a subject that is not a UUID; no {@link PermissionCache};
 * no {@link ActionSource} bean; no {@code core.user_account} row for the user; and any runtime error
 * from the cache or the source — a {@code CacheOperationException} when Redis is unreachable included.
 * An authorization check that degrades to "allow" is worse than an outage (spec section 9). The same
 * null-safety as the frozen check, which answered {@code false} to any missing argument
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:85-86}).
 *
 * <p>The collaborators arrive as suppliers because each may legitimately be absent from a context,
 * and absence has to mean "refuse" at call time, not "fail to start".
 */
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final Supplier<PermissionCache> cache;
    private final Supplier<ActionSource> actionSource;
    private final UserAccountIdResolver userAccounts;

    public PermissionService(
            Supplier<PermissionCache> cache, Supplier<ActionSource> actionSource, UserAccountIdResolver userAccounts) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.actionSource = Objects.requireNonNull(actionSource, "actionSource must not be null");
        this.userAccounts = Objects.requireNonNull(userAccounts, "userAccounts must not be null");
    }

    /**
     * Returns the full set of action codes held by the caller in the bound tenant,
     * read from {@link PermissionCache} under the current permission version (W-12.3).
     * Fails closed by returning an empty set on any error or missing context.
     */
    public Set<String> currentActions() {
        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            log.warn("Permission check refused: no tenant bound to this request");
            return Set.of();
        }
        Optional<UUID> userId = currentUserId();
        if (userId.isEmpty()) {
            log.warn("Permission check refused: no authenticated user with a UUID subject");
            return Set.of();
        }
        try {
            PermissionCache permissionCache = cache.get();
            if (permissionCache == null) {
                log.warn("Permission check refused: no PermissionCache is configured");
                return Set.of();
            }
            ActionSource source = actionSource.get();
            if (source == null) {
                log.warn("Permission check refused: no ActionSource bean is configured");
                return Set.of();
            }
            return permissionCache.actionsOf(
                    tenantId.get(), userId.get(), () -> load(source, tenantId.get(), userId.get()));
        } catch (RuntimeException e) {
            log.warn(
                    "Permission check failed for user {} in tenant {} and fails closed",
                    userId.get(),
                    tenantId.get(),
                    e);
            return Set.of();
        }
    }

    /** True only if the current caller demonstrably holds {@code actionCode}; false on any doubt. */
    public boolean holds(String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            log.warn("Permission refused: no action code given");
            return false;
        }
        Set<String> actions = currentActions();
        if (actions.contains(actionCode)) {
            return true;
        }
        currentUserId().ifPresent(uid -> TenantContext.current()
                .ifPresent(tid -> log.info(
                        "Permission refused for {}: user {} does not hold it in tenant {}", actionCode, uid, tid)));
        return false;
    }

    /**
     * Returns if the caller holds {@code actionCode}.
     *
     * @throws PermissionDeniedException otherwise, answered {@code 403}
     */
    public void require(String actionCode) {
        if (!holds(actionCode)) {
            throw new PermissionDeniedException(actionCode);
        }
    }

    /** The cache-miss path. Throws rather than returning empty when the user cannot be resolved. */
    private Set<String> load(ActionSource source, UUID tenantId, UUID keycloakUserId) {
        // An empty set here would be cached, and would keep refusing a user whose profile row is
        // created a moment later until the TTL ran out. Throwing caches nothing.
        UUID userAccountId = userAccounts
                .userAccountIdOf(tenantId, keycloakUserId)
                .orElseThrow(() -> new IllegalStateException("No core.user_account row for user " + keycloakUserId
                        + " in tenant " + tenantId + "; UserProfileSyncFilter has not synced it"));
        Set<String> actions = source.actionsOf(tenantId, userAccountId);
        return actions == null ? Set.of() : actions;
    }

    /**
     * The Keycloak subject — {@code sub}, as {@code UserProfileSyncFilter.subjectOf} reads it — or,
     * for a principal that is not a {@link Jwt}, the authentication's name, as
     * {@code TenantAuthenticationExtractor} falls back to. Empty unless it is a UUID.
     */
    private static Optional<UUID> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String subject = auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : auth.getName();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            log.warn("Token subject is not a UUID: {}", subject);
            return Optional.empty();
        }
    }
}
