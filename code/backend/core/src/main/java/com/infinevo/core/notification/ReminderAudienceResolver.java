package com.infinevo.core.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a reminder rule's audience into recipient employee IDs for a given tenant (W-20.2,
 * contracts §5 row 15, §6 decision 7).
 *
 * <p>Each audience type (e.g. SUBJECT, MANAGER, HR) is supplied by an implementing bean,
 * allowing other modules (HRMS, Payroll) to contribute recipient resolution without Core
 * coupling to them.
 *
 * <p>An audience that knows something the sweep cannot, such as which week it checked or who in a manager's team is
 * late, supplies those values itself (W-43.1): it names them in {@link #suppliedPlaceholders()} and returns them with
 * each recipient from {@link #recipients}. An audience that supplies nothing implements only {@link #resolve} and
 * behaves as before.
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

    /**
     * The placeholder names this audience puts into the mail itself, beyond the ones the sweep supplies. A rule whose
     * event needs one of them is accepted for this audience and refused for any that does not supply it.
     * Default: none.
     */
    default Set<String> suppliedPlaceholders() {
        return Set.of();
    }

    /**
     * The recipients of the rule, each with the values this audience supplies for them. The sweep calls this, not
     * {@link #resolve}, and the same rules apply to it: tenant bound, no transaction open, so a database read opens its
     * own.
     *
     * <p>Default: {@link #resolve}'s ids, with no values, so an audience written before this method behaves as before.
     *
     * @param slotDate the tenant-local day the sweep is sending for, so an audience counts weeks in the tenant's
     *     zone and not the server's
     */
    default List<ReminderRecipient> recipients(ReminderRule rule, UUID tenantId, LocalDate slotDate) {
        return resolve(rule, tenantId).stream().map(ReminderRecipient::of).toList();
    }
}
