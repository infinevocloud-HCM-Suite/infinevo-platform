package com.infinevo.core.authz;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The role and grant use cases (W-11.1, spec section 4), and the failures a caller can act on.
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding filter
 * set from the verified token (W-08). All logic is in {@link RoleServiceImpl} —
 * {@code docs/CONVENTIONS.md} section 3.
 *
 * <p><strong>No permission check lives here.</strong> Who may call these is {@code W-11.2}'s; this
 * ticket stores the answer and does not enforce it (spec section 2).
 */
public interface RoleService {

    /** The action catalogue, ordered by code. The same for every tenant. */
    List<ActionResponse> listActions();

    /** Creates a tenant role holding the given actions. */
    RoleResponse create(RoleCreateRequest request);

    /** Every role in the bound tenant — the seven system roles and the tenant's own — with its actions. */
    List<RoleResponse> list();

    /** Renames a tenant role and replaces its action set. Refused for a system role. */
    RoleResponse update(UUID id, RoleUpdateRequest request);

    /** Deletes a tenant role. Refused for a system role, and while any user holds it. */
    void delete(UUID id);

    /**
     * Replaces the set of roles a user holds in the bound tenant. Refused when it would take
     * {@code tenant-admin} from the caller, or from the tenant's last active holder (W-73.4).
     */
    UserRolesResponse replaceUserRoles(UUID userAccountId, UserRolesRequest request);

    /**
     * Refuses disabling the caller's own account, or the tenant's last active {@code tenant-admin} (W-73.4)
     * — the same rule as {@link #replaceUserRoles}, so an admin cannot lock the tenant out either way.
     */
    void requireCanDisable(UUID userAccountId);

    /** Whether the user holds the seeded {@code tenant-admin} role in the bound tenant (W-73.4). */
    boolean holdsTenantAdmin(UUID userAccountId);

    /**
     * Refuses a change that would leave the caller without {@code tenant-admin}, or the tenant with no
     * active holder of it (W-73.4 §2). Maps to {@code 409}; the message says which and what to do.
     */
    class AdminGuardException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public AdminGuardException(String message) {
            super(message);
        }
    }

    /** No such role or user in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * The request cannot be applied — including an action code the catalogue does not hold. Maps to
     * {@code 400} with per-field detail.
     */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldErrors;

        public ValidationException(Map<String, String> fieldErrors) {
            super("The request was not valid: " + fieldErrors);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }

    /** This tenant already has a role with this code. Maps to {@code 409}. */
    class DuplicateCodeException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public DuplicateCodeException(String code) {
            super("A role with code " + code + " already exists in this tenant");
        }
    }

    /**
     * The role is one of the seven seeded for every tenant and cannot be changed. Maps to {@code 409}.
     *
     * <p>A conflict with the row's state rather than a {@code 403}: nobody may edit a system role, so
     * it is not a question of who is asking — which is {@code W-11.2}'s question, not this one.
     */
    class SystemRoleException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public SystemRoleException(String code, String verb) {
            super("Role " + code + " is a system role and cannot be " + verb
                    + ". Create a role of your own with the actions you need instead.");
        }
    }

    /** The role is granted to at least one user. Maps to {@code 409}, as the spec's DELETE row says. */
    class RoleInUseException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public RoleInUseException(String code, long holders) {
            super("Role " + code + " cannot be deleted: " + holders
                    + (holders == 1 ? " user holds" : " users hold")
                    + " it. Revoke it from them first.");
        }
    }
}
