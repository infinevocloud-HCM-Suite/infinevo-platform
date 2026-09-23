package com.infinevo.shared.identity;

import com.infinevo.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the token's claims into {@code core.user_account} on the way in (W-10, spec section 3).
 *
 * <p>Runs <strong>after</strong> {@code TenantContextFilter}, which is the whole point of the
 * ordering in {@link UserProfileSyncConfig}: by the time this filter runs the tenant is bound, the
 * membership in {@code core.user_tenant} has been verified, and a user without one has already been
 * answered {@code 401}. So this filter never provisions and never has to decide which tenant a
 * profile belongs to — it is told.
 *
 * <p>It does nothing at all unless both conditions hold: a tenant is bound and the principal is a
 * {@link Jwt}. A request on an exempt path, or one authenticated some other way in a test, passes
 * straight through.
 */
public class UserProfileSyncFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserProfileSyncFilter.class);

    /** Standard OIDC claims. Keycloak emits all three from its built-in profile and email mappers. */
    private static final String CLAIM_EMAIL = "email";

    private static final String CLAIM_GIVEN_NAME = "given_name";
    private static final String CLAIM_FAMILY_NAME = "family_name";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    private final UserProfileSyncService syncService;

    public UserProfileSyncFilter(UserProfileSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Optional<UUID> tenantId = TenantContext.current();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (tenantId.isPresent() && auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            UUID userId = subjectOf(jwt);
            if (userId != null) {
                // Contained on purpose. This is bookkeeping running beside the real request: the
                // caller asked for something else, and the token has already been validated and
                // the tenant already bound, so the request is legitimate whether or not the
                // profile row gets refreshed. Letting a transient database error - or the unique
                // index losing the documented first-request race - escape here would fail a read
                // that needs no write at all, and the caller would see it as an auth failure.
                // Losing a sync costs the freshness of a name until the next request; failing the
                // request costs the request. Logged at warn so it is visible rather than silent.
                try {
                    UserProfileSyncService.SyncOutcome outcome = syncService.sync(
                            tenantId.get(),
                            userId,
                            emailOf(jwt),
                            jwt.getClaimAsString(CLAIM_GIVEN_NAME),
                            jwt.getClaimAsString(CLAIM_FAMILY_NAME));
                    if (outcome != UserProfileSyncService.SyncOutcome.UNCHANGED) {
                        log.info("user_account {} for user {} in tenant {}", outcome, userId, tenantId.get());
                    }
                } catch (RuntimeException e) {
                    log.warn(
                            "user_account sync failed for user {} in tenant {}; the request continues",
                            userId,
                            tenantId.get(),
                            e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * {@code sub}, which for Keycloak is the user's UUID and is pinned per user in the dev realm —
     * {@code infra/docker/keycloak/dev-realm.json}, and the same three values in
     * {@code infra/docker/seed/02-user-tenants.sql}.
     *
     * <p>Returns {@code null} rather than throwing if it is not a UUID: the request has already been
     * authenticated and had a tenant bound, so refusing it here would be a new failure mode invented
     * by a cache. Nothing is synced instead.
     */
    private static UUID subjectOf(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            log.warn("Token subject is not a UUID, skipping profile sync: {}", subject);
            return null;
        }
    }

    /**
     * {@code email} is {@code NOT NULL} in {@code core.user_account} ({@code V009:8}), so a token
     * without the claim falls back to {@code preferred_username} and then to {@code sub}. An
     * unverified account with no email address is Keycloak's business; it is not a reason to refuse
     * the request.
     */
    private static String emailOf(Jwt jwt) {
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        if (email != null && !email.isBlank()) {
            return email;
        }
        String username = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        return username != null && !username.isBlank() ? username : jwt.getSubject();
    }
}
