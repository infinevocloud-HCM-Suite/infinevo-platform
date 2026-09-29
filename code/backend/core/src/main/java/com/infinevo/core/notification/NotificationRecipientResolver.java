package com.infinevo.core.notification;

import java.util.Optional;
import java.util.UUID;

/**
 * Which employee the caller is, so the in-app endpoints show a person only their own notifications
 * (W-20.1, "recipient only").
 *
 * <p>{@link EmployeeNotificationRecipientResolver} answers it from {@code W-13.4}'s login link. A seam
 * so the integration tests can name the caller's employee directly. A caller linked to no employee is
 * nobody, so their inbox is empty rather than anyone else's — fail closed.
 */
public interface NotificationRecipientResolver {

    /** The bound caller's employee id in the bound tenant, or empty if the login is linked to none. */
    Optional<UUID> currentEmployeeId();
}
