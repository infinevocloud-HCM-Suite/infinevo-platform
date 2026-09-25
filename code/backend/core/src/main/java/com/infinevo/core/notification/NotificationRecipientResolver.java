package com.infinevo.core.notification;

import java.util.Optional;
import java.util.UUID;

/**
 * Which employee the caller is, so the in-app endpoints show a person only their own notifications
 * (W-20.1, "recipient only").
 *
 * <p>A seam because the answer is {@code W-13.4}'s: nothing links a login to an employee until it adds
 * {@code core.employee.user_account_id} and {@code EmployeeService.currentEmployee()}. When it merges,
 * {@link UnlinkedNotificationRecipientResolver} is replaced by one line calling that method. Until then
 * the answer is always "nobody", so the inbox is empty rather than anyone else's — fail closed.
 */
public interface NotificationRecipientResolver {

    /** The bound caller's employee id in the bound tenant, or empty if the login is linked to none. */
    Optional<UUID> currentEmployeeId();
}
