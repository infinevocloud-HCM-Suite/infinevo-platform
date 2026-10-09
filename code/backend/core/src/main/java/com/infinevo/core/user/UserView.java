package com.infinevo.core.user;

import java.util.List;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/users} (W-73.4 §4): a user account in the bound tenant, the roles it holds and
 * the employee it is linked to, if any. {@code enabled} is {@code core.user_account.status = 'ACTIVE'}.
 */
public record UserView(
        UUID id,
        String email,
        String displayName,
        List<RoleRef> roles,
        UUID employeeId,
        String employeeNumber,
        boolean enabled) {

    /** A role as the screen shows it: id to send back on Change roles, code and name to read. */
    public record RoleRef(UUID id, String code, String name) {}
}
