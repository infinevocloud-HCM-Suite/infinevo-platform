package com.infinevo.core.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * Extension contract for resolving approvers for kinds that require external resolution (W-15.1, spec section 4).
 * Beans implementing this are keyed by {@link #kind()}.
 */
public interface ApproverResolver {

    ApproverKind kind();

    Optional<UUID> resolve(UUID tenantId, UUID employeeId, String contextRef);
}
