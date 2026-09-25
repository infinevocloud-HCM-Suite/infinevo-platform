package com.infinevo.core.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@link NotificationRecipientResolver} until {@code W-13.4} links a login to an employee: nobody
 * is anybody, so every inbox is empty and nothing can be marked read. Replace the body with
 * {@code employeeService.currentEmployee().map(EmployeeResponse::id)} when that method exists.
 */
@Component
class UnlinkedNotificationRecipientResolver implements NotificationRecipientResolver {

    @Override
    public Optional<UUID> currentEmployeeId() {
        return Optional.empty();
    }
}
