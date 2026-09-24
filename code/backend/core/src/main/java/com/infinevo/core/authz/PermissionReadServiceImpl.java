package com.infinevo.core.authz;

import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PermissionReadService} — one query, {@code user_role} joined to {@code role_action}, in the
 * bound tenant (W-11.1).
 */
@Service
public class PermissionReadServiceImpl implements PermissionReadService {

    private final RoleActionRepository roleActionRepository;

    public PermissionReadServiceImpl(RoleActionRepository roleActionRepository) {
        this.roleActionRepository =
                Objects.requireNonNull(roleActionRepository, "roleActionRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> actionsOf(UUID userAccountId) {
        Objects.requireNonNull(userAccountId, "userAccountId must not be null");
        UUID tenantId = TenantContext.require();
        return Set.copyOf(roleActionRepository.findActionCodesOfUser(tenantId, userAccountId));
    }
}
