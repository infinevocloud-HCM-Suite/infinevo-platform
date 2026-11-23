package com.infinevo.core.ai;

import java.util.Objects;
import java.util.UUID;

/**
 * The caller a tool runs for (W-114.3): the bound tenant, the token subject, and the employee record, which is
 * {@code null} for a user with no employee record.
 */
public record AiToolContext(UUID tenantId, UUID userId, UUID employeeId) {

    public AiToolContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
