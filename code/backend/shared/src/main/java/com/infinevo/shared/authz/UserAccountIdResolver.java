package com.infinevo.shared.authz;

import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserProfileSyncService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Maps the authenticated Keycloak subject to its {@code core.user_account.id} in a tenant (W-11.2).
 *
 * <p>{@link ActionSource} is keyed on the profile row, because that is what {@code core.user_role}
 * references ({@code V023__user_role.sql:7}); the token carries only the Keycloak subject. The two are
 * one-to-one within a tenant — the unique index {@code (tenant_id, keycloak_user_id)}
 * ({@code V009__user_account.sql:25}).
 */
public interface UserAccountIdResolver {

    /** The profile row's id, or empty when the user has no profile in that tenant yet. */
    Optional<UUID> userAccountIdOf(UUID tenantId, UUID keycloakUserId);

    /**
     * The default: the same lookup {@code GET /api/v1/me} makes — {@link UserProfileSyncService#find}
     * ({@code MeController}). The row exists by the time a controller runs, because
     * {@code UserProfileSyncFilter} creates it on the way in; if that sync failed there is no row, and
     * the check is refused.
     *
     * <p>Resolved lazily through the provider, so a context without the identity service — a slice
     * test — still starts, and refuses every check instead.
     */
    final class FromUserProfiles implements UserAccountIdResolver {

        private final ObjectProvider<UserProfileSyncService> profiles;

        public FromUserProfiles(ObjectProvider<UserProfileSyncService> profiles) {
            this.profiles = profiles;
        }

        @Override
        public Optional<UUID> userAccountIdOf(UUID tenantId, UUID keycloakUserId) {
            UserProfileSyncService service = profiles.getIfAvailable();
            if (service == null) {
                return Optional.empty();
            }
            return service.find(tenantId, keycloakUserId).map(UserAccount::getId);
        }
    }
}
