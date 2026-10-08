package com.infinevo.shared.identity;

import com.infinevo.shared.authz.RoleSource;
import com.infinevo.shared.impersonation.ActingAs;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/me} — who the bearer token belongs to, in which tenant, and as what (W-10, spec
 * section 4; roles and display name from W-73.1).
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
 * would see. A bootstrap session has no such user; it answers with the staff member's own token claims and
 * the one role a bootstrap session stands in for, {@code tenant-admin}.
 *
 * <p>The roles come from {@link RoleSource}, {@code core}'s port, through a provider: a context without
 * it — the identity slice tests — still starts, and answers with no roles.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    /** What a bootstrap session acts as ({@code ActingAs.Impersonation#actionCodes}: the tenant-admin grants). */
    static final String BOOTSTRAP_ROLE = "tenant-admin";

    private final UserProfileSyncService syncService;
    private final Supplier<RoleSource> roleSource;

    /** No role source: {@code roles} is always empty. For contexts and tests that predate W-73.1. */
    public MeController(UserProfileSyncService syncService) {
        this(syncService, () -> null);
    }

    @Autowired
    public MeController(UserProfileSyncService syncService, ObjectProvider<RoleSource> roleSource) {
        this(syncService, roleSource::getIfAvailable);
    }

    MeController(UserProfileSyncService syncService, Supplier<RoleSource> roleSource) {
        this.syncService = Objects.requireNonNull(syncService, "syncService must not be null");
        this.roleSource = Objects.requireNonNull(roleSource, "roleSource must not be null");
    }

    /**
     * The response body. {@code tenantId} is included because the client never chose it.
     *
     * @param displayName first and last name, or the email when the profile has neither (W-73.1)
     * @param roles the codes of the roles held in the bound tenant, sorted; empty when none (W-73.1)
     */
    public record MeView(
            UUID userId,
            String email,
            String firstName,
            String lastName,
            UUID tenantId,
            String displayName,
            List<String> roles) {

        public MeView {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

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
        return view(account, rolesOf(tenantId, account.getId()));
    }

    private MeView actingView(ActingAs.Impersonation session, Jwt jwt, UUID tenantId) {
        if (session.isBootstrap()) {
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");
            String email = jwt.getClaimAsString("email");
            return new MeView(
                    UUID.fromString(jwt.getSubject()),
                    email,
                    firstName,
                    lastName,
                    tenantId,
                    displayName(firstName, lastName, email),
                    List.of(BOOTSTRAP_ROLE));
        }
        UserAccount account = syncService
                .findById(tenantId, session.targetUserAccountId())
                .orElseThrow(() -> new IllegalStateException("Impersonation session " + session.sessionId()
                        + " names user account " + session.targetUserAccountId() + ", which is not in tenant "
                        + tenantId + ". The session check should have refused it."));
        return view(account, rolesOf(tenantId, session.targetUserAccountId()));
    }

    private static MeView view(UserAccount account, List<String> roles) {
        return new MeView(
                account.getKeycloakUserId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getTenantId(),
                displayName(account.getFirstName(), account.getLastName(), account.getEmail()),
                roles);
    }

    /** First and last name, whichever are present; the email when neither is. */
    static String displayName(String firstName, String lastName, String email) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? (email == null ? "" : email) : name;
    }

    /**
     * The user's role codes, sorted. No source, or a source that fails, means no chips — never a 500 on the
     * one endpoint every page loads.
     */
    private List<String> rolesOf(UUID tenantId, UUID userAccountId) {
        RoleSource source = roleSource.get();
        if (source == null) {
            return List.of();
        }
        try {
            Set<String> codes = source.roleCodesOf(tenantId, userAccountId);
            return codes == null ? List.of() : codes.stream().sorted().toList();
        } catch (RuntimeException e) {
            log.warn(
                    "Roles could not be read for user account {} in tenant {}: {}",
                    userAccountId,
                    tenantId,
                    e.toString());
            return List.of();
        }
    }
}
