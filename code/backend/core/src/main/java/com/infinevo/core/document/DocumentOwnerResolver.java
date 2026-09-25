package com.infinevo.core.document;

import java.util.Optional;
import java.util.UUID;

/**
 * Which employee the caller is, for {@code core.document.read_own} (W-21, spec section 4).
 *
 * <p>A seam of its own because the answer belongs to {@code W-13.4}, which links a login to an
 * employee and adds {@code EmployeeService.currentEmployee()}. Until it merges,
 * {@link UnlinkedDocumentOwnerResolver} answers "nobody", so {@code read_own} admits no one — it fails
 * closed rather than guessing from an email address.
 */
public interface DocumentOwnerResolver {

    /** The caller's employee id in the bound tenant, or empty when the login is linked to none. */
    Optional<UUID> currentEmployeeId();
}
