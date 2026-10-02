package com.infinevo.shared.impersonation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves an impersonation session by calling database function
 * {@code core.resolve_impersonation(p_session_id, p_platform_user_id)} (W-65.2).
 */
@FunctionalInterface
public interface ImpersonationResolver {

    /**
     * Details of an active, verified impersonation session returned by the database.
     *
     * @param sessionId           the database session ID
     * @param tenantId            the target customer tenant ID to bind
     * @param platformUserId      the staff user ID who opened the session
     * @param targetUserAccountId the user account ID being acted as, or {@code null} for bootstrap
     * @param targetEmail         the target user's email, or {@code null} for bootstrap
     * @param actionCodes         the actions granted to the session
     */
    record ResolvedImpersonation(
            UUID sessionId,
            UUID tenantId,
            UUID platformUserId,
            UUID targetUserAccountId,
            String targetEmail,
            Set<String> actionCodes) {

        public ResolvedImpersonation {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(platformUserId, "platformUserId must not be null");
            actionCodes = actionCodes == null ? Set.of() : Set.copyOf(actionCodes);
        }

        public boolean isBootstrap() {
            return targetUserAccountId == null;
        }
    }

    /**
     * Resolves an impersonation session for the given staff user.
     *
     * @param sessionId      the session UUID from the {@code X-Impersonation} header
     * @param platformUserId the authenticated platform staff user ID
     * @return the resolved session details, or {@link Optional#empty()} if invalid, expired, or unowned
     */
    Optional<ResolvedImpersonation> resolve(UUID sessionId, UUID platformUserId);
}
