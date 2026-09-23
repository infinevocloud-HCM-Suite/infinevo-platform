package com.infinevo.shared.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code core.user_account} in step with the token claims (W-10, spec section 7).
 *
 * <p>Three cases, and the third is the one worth naming:
 *
 * <ul>
 *   <li><strong>Insert</strong> — first request after a login, no row yet.
 *   <li><strong>Update</strong> — a claim changed in Keycloak since the last request.
 *   <li><strong>Nothing</strong> — the claims match the row. <em>No write at all.</em> An
 *       unconditional {@code save()} would rewrite {@code updated_at} on every single request,
 *       turning a profile cache into the busiest table in the database and making the audit trail
 *       unreadable.
 * </ul>
 *
 * <p>The tenant is a parameter here rather than a read of {@code TenantContext} so this class can
 * be unit-tested without a bound thread; the one caller that matters, {@link UserProfileSyncFilter},
 * takes it from {@code TenantContext} and never from the request.
 *
 * <p><strong>This does not provision.</strong> A user with no {@code core.user_tenant} row never
 * reaches here — {@code TenantContextFilter} has already answered {@code 401 TENANT_NOT_BOUND}.
 * That is spec section 13, decision 2: {@code W-24} (#28) issues invitations, {@code W-10} does not.
 */
@Service
public class UserProfileSyncService {

    /** What {@link #sync} did. Returned so the filter can log it and the tests can assert on it. */
    public enum SyncOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    private final UserAccountRepository repository;

    public UserProfileSyncService(UserAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates or refreshes the profile for {@code (tenantId, keycloakUserId)}.
     *
     * <p>Runs in its own transaction, which is also what binds the tenant onto the JDBC connection
     * — the binding is transaction-local ({@code D-57}, {@code TenantBindingDataSourceProxy}).
     *
     * <p>Two requests from a brand-new user arriving at the same instant can both find no row and
     * both insert; the unique index {@code (tenant_id, keycloak_user_id)} ({@code V009:25}) refuses
     * the second, which fails that one request with a 500 and succeeds on retry. Left as is
     * deliberately: the alternative is an upsert that hides a constraint the table should keep
     * enforcing, for a window of a few milliseconds once in a user's lifetime.
     */
    @Transactional
    public SyncOutcome sync(UUID tenantId, UUID keycloakUserId, String email, String firstName, String lastName) {
        if (tenantId == null || keycloakUserId == null) {
            throw new IllegalArgumentException("tenantId and keycloakUserId are required to sync a profile");
        }
        Instant now = Instant.now();

        Optional<UserAccount> existing = repository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId);
        if (existing.isEmpty()) {
            repository.save(new UserAccount(tenantId, keycloakUserId, email, firstName, lastName, now));
            return SyncOutcome.CREATED;
        }

        UserAccount account = existing.get();
        if (!account.differsFrom(email, firstName, lastName)) {
            // Nothing to write. Not even last_synced_at: see the class comment.
            return SyncOutcome.UNCHANGED;
        }

        account.applyClaims(email, firstName, lastName, now);
        repository.save(account);
        return SyncOutcome.UPDATED;
    }

    /** The profile of one user in one tenant, for {@code GET /api/v1/me}. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> find(UUID tenantId, UUID keycloakUserId) {
        return repository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId);
    }
}
