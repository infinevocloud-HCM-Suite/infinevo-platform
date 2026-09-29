package com.infinevo.core.notification;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the reference anchor date from which a reminder rule's {@code offset_days}
 * is counted (W-20.2).
 */
public interface ReminderAnchorResolver {

    /** The anchor handled by this resolver (e.g. DECLARATION_LOCK_DATE, POI_DUE_DATE, LEAVE_START). */
    Anchor anchor();

    /** Resolves the target anchor date for the rule in the given tenant, if applicable. */
    Optional<LocalDate> resolveAnchorDate(ReminderRule rule, UUID tenantId);
}
