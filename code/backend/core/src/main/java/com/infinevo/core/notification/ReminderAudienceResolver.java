package com.infinevo.core.notification;

import java.util.List;
import java.util.UUID;

/**
 * Turns a reminder rule's audience into recipient employee IDs for a given tenant (W-20.2,
 * contracts §5 row 15, §6 decision 7).
 *
 * <p>Each audience type (e.g. SUBJECT, MANAGER, HR) is supplied by an implementing bean,
 * allowing other modules (HRMS, Payroll) to contribute recipient resolution without Core
 * coupling to them.
 */
public interface ReminderAudienceResolver {

    /** The audience name handled by this resolver (e.g. "SUBJECT", "MANAGER", "HR"). */
    String audience();

    /**
     * Resolves the recipient employee IDs in the given tenant matching the rule.
     *
     * <p>The reminder sweep calls this with the tenant bound and no transaction open. An
     * implementation that reads the database opens its own ({@code @Transactional(readOnly = true)}):
     * the tenant binding is transaction-local, and a read on an auto-commit connection is refused
     * ({@code TenantContext.setForConnection}, D-57).
     */
    List<UUID> resolve(ReminderRule rule, UUID tenantId);
}
