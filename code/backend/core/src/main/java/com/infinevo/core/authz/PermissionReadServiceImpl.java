package com.infinevo.core.authz;

import com.infinevo.shared.authz.ActionSource;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PermissionReadService} — one query, {@code user_role} joined to {@code role_action}, in the
 * bound tenant (W-11.1) — and {@code shared}'s {@link ActionSource} port, which the permission check
 * loads through on a cache miss (W-11.2, spec section 13 decision 3).
 *
 * <p><strong>The explicit tenant is used, and it must be the bound one.</strong> {@code shared}'s
 * {@code PermissionService} passes the tenant it keys the cache on; the query names that tenant, and
 * row-level security enforces it again on the connection, which is bound from {@link TenantContext}.
 * So the two must agree. A tenant argument that differs from the bound one, or no bound tenant at
 * all, is refused with an exception rather than answered: an answer would be read under the
 * <em>bound</em> tenant's policy — or none — and cached under the argument's key. An exception is
 * cached by nobody, and {@code PermissionService} turns it into a refusal.
 *
 * <p>This class does not bind the tenant itself. {@link TenantContext} is set at the edge — the
 * filter or the job runner — never by business code.
 */
@Service
public class PermissionReadServiceImpl implements PermissionReadService, ActionSource {

    private final RoleActionRepository roleActionRepository;

    public PermissionReadServiceImpl(RoleActionRepository roleActionRepository) {
        this.roleActionRepository =
                Objects.requireNonNull(roleActionRepository, "roleActionRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> actionsOf(UUID userAccountId) {
        Objects.requireNonNull(userAccountId, "userAccountId must not be null");
        return query(TenantContext.require(), userAccountId);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if no tenant is bound, or the bound tenant is not {@code tenantId}
     */
    @Override
    @Transactional(readOnly = true)
    public Set<String> actionsOf(UUID tenantId, UUID userAccountId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userAccountId, "userAccountId must not be null");
        UUID bound = TenantContext.require();
        if (!bound.equals(tenantId)) {
            throw new IllegalStateException("Refusing to read tenant " + tenantId + "'s actions while tenant " + bound
                    + " is bound; row-level security would answer for the wrong tenant");
        }
        return query(tenantId, userAccountId);
    }

    private Set<String> query(UUID tenantId, UUID userAccountId) {
        return Set.copyOf(roleActionRepository.findActionCodesOfUser(tenantId, userAccountId));
    }
}
