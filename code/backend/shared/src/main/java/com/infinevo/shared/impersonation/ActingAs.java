package com.infinevo.shared.impersonation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the impersonation context for the current request thread (W-65.2).
 *
 * <p>Set once, at the edge, by {@code TenantContextFilter} when an {@code X-Impersonation} header is
 * presented and successfully resolved. Never by business code.
 *
 * <p><strong>Always clear it in a {@code finally} block.</strong> Threads are pooled and reused; an
 * impersonation context left behind would let a subsequent unrelated request act as another user.
 * It must always be cleared in the same {@code finally} block as {@code TenantContext.clear()}.
 */
public final class ActingAs {

    /**
     * Record representing an active impersonation session.
     *
     * @param platformUserId      the Keycloak subject of the platform staff user
     * @param targetUserAccountId the customer tenant user account ID being acted as, or {@code null}
     *                            for a bootstrap session
     * @param sessionId           the database session ID in {@code core.impersonation_session}
     * @param targetEmail         the email of the target user, or {@code null} for bootstrap
     * @param actionCodes         the resolved action codes for the session (target user's grants, or
     *                            {@code tenant-admin} role grants for a bootstrap session)
     */
    public record Impersonation(
            UUID platformUserId,
            UUID targetUserAccountId,
            UUID sessionId,
            String targetEmail,
            Set<String> actionCodes) {

        public Impersonation {
            Objects.requireNonNull(platformUserId, "platformUserId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            actionCodes = actionCodes == null ? Set.of() : Set.copyOf(actionCodes);
        }

        /** True if this is a bootstrap session (no target user account). */
        public boolean isBootstrap() {
            return targetUserAccountId == null;
        }
    }

    private static final ThreadLocal<Impersonation> CURRENT = new ThreadLocal<>();

    private ActingAs() {}

    /** Binds the impersonation context for this thread. Rejects null. */
    public static void set(Impersonation impersonation) {
        Objects.requireNonNull(impersonation, "impersonation must not be null");
        CURRENT.set(impersonation);
    }

    /** Convenience factory method to construct and bind an impersonation session context. */
    public static void set(
            UUID platformUserId,
            UUID targetUserAccountId,
            UUID sessionId,
            String targetEmail,
            Set<String> actionCodes) {
        set(new Impersonation(platformUserId, targetUserAccountId, sessionId, targetEmail, actionCodes));
    }

    /** The current impersonation context if active. */
    public static Optional<Impersonation> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Alias for {@link #current()}. */
    public static Optional<Impersonation> get() {
        return current();
    }

    /**
     * Returns the active impersonation context, or throws {@link IllegalStateException} if none is
     * bound.
     */
    public static Impersonation require() {
        Impersonation context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No impersonation session active on this thread.");
        }
        return context;
    }

    /** Returns {@code true} if an impersonation session is active on the current thread. */
    public static boolean isActing() {
        return CURRENT.get() != null;
    }

    /** Clears the impersonation context for this thread. Call from a {@code finally} block, always. */
    public static void clear() {
        CURRENT.remove();
    }
}
