package com.infinevo.shared.identity;

import com.infinevo.shared.tenant.TenantContext;
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
}
