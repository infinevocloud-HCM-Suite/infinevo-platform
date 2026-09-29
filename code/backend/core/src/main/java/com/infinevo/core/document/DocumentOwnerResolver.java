package com.infinevo.core.document;

import java.util.Optional;
import java.util.UUID;

/**
 * Which employee the caller is, for {@code core.document.read_own} (W-21, spec section 4).
 *
 * <p>{@link EmployeeDocumentOwnerResolver} answers it from {@code W-13.4}'s login link. A seam of its
 * own so the integration tests can name the caller's employee directly, without seeding a linked
 * login. A caller linked to no employee is nobody: {@code read_own} fails closed rather than guessing
 * from an email address.
 */
public interface DocumentOwnerResolver {

    /** The caller's employee id in the bound tenant, or empty when the login is linked to none. */
    Optional<UUID> currentEmployeeId();
}
