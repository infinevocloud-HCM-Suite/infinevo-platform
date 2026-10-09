package com.infinevo.core.invitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Whether an employee can sign in, for the Access badge on the employee page (W-73.3 §4).
 *
 * <p>{@code invitationId} and {@code expiresAt} are set only for {@link State#INVITED}.
 */
public record EmployeeAccessResponse(State state, UUID invitationId, Instant expiresAt, List<RoleRef> roles) {

    public enum State {
        NONE,
        INVITED,
        ACTIVE
    }

    public record RoleRef(UUID id, String code, String name) {}
}
