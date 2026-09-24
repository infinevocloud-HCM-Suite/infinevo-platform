package com.infinevo.shared.authz;

import org.springframework.security.access.AccessDeniedException;

/**
 * The caller does not hold the action, or the check could not be made (W-11.2).
 *
 * <p>An {@link AccessDeniedException}, so anything that already understands Spring Security's refusal
 * treats it as one. {@link AuthzExceptionHandler} answers it {@code 403 FORBIDDEN} in the shared
 * envelope.
 */
public class PermissionDeniedException extends AccessDeniedException {

    private final String actionCode;

    public PermissionDeniedException(String actionCode) {
        super("Requires action '" + actionCode + "'");
        this.actionCode = actionCode;
    }

    /** The action that was required. */
    public String actionCode() {
        return actionCode;
    }
}
