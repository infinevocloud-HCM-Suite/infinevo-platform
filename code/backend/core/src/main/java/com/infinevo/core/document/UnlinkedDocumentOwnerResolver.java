package com.infinevo.core.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@link DocumentOwnerResolver} until {@code W-13.4} links a login to an employee: nobody is
 * anybody, so {@code core.document.read_own} admits no one. Replace the body with
 * {@code employeeService.currentEmployee().map(EmployeeResponse::id)} when that method exists — the
 * same change {@code UnlinkedNotificationRecipientResolver} waits for.
 */
@Component
class UnlinkedDocumentOwnerResolver implements DocumentOwnerResolver {

    @Override
    public Optional<UUID> currentEmployeeId() {
        return Optional.empty();
    }
}
