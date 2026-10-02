package com.infinevo.shared.identity;

import com.infinevo.shared.impersonation.ActingAs;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/me} — who the bearer token belongs to, and in which tenant (W-10, spec
 * section 4).
 *
 * <p>W-10 adds no other endpoint. Login is a browser redirect to Keycloak and the backend only
 * validates; this one exists so that a login can be proved end to end without waiting for
 * {@code W-13}.
 *
 * <p>Thin, like every controller here: it reads the profile the sync filter has already written and
 * repacks it. The tenant is never a parameter and is never echoed from the request — it comes from
 * {@link TenantContext}, bound by {@code TenantContextFilter} after membership was verified.
 *
 * <p>While platform staff act inside a tenant (W-65.2) there is no row for the staff member there — the
 * sync filter skips them on purpose — so the answer is the user being acted as, which is what the customer
 * would see. A bootstrap session has no such user; it answers with the staff member's own token claims.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserProfileSyncService syncService;

    public MeController(UserProfileSyncService syncService) {
        this.syncService = syncService;
    }

    /** The response body. {@code tenantId} is included because the client never chose it. */
    public record MeView(UUID userId, String email, String firstName, String lastName, UUID tenantId) {}

    @GetMapping
    public MeView me(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = TenantContext.require();
        Optional<ActingAs.Impersonation> acting = ActingAs.current();
        if (acting.isPresent()) {
            return actingView(acting.get(), jwt, tenantId);
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAccount account = syncService
                .find(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("No core.user_account row for user " + userId
                        + " in tenant " + tenantId + ". The request reached the controller without passing "
                        + "UserProfileSyncFilter, which should be impossible — check the filter order."));
        return new MeView(
                account.getKeycloakUserId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getTenantId());
    }

    private MeView actingView(ActingAs.Impersonation session, Jwt jwt, UUID tenantId) {
        if (session.isBootstrap()) {
            return new MeView(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("given_name"),
                    jwt.getClaimAsString("family_name"),
                    tenantId);
        }
        UserAccount account = syncService
                .findById(tenantId, session.targetUserAccountId())
                .orElseThrow(() -> new IllegalStateException("Impersonation session " + session.sessionId()
                        + " names user account " + session.targetUserAccountId() + ", which is not in tenant "
                        + tenantId + ". The session check should have refused it."));
        return new MeView(
                account.getKeycloakUserId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getTenantId());
    }
}
