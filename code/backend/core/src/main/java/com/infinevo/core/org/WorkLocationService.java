package com.infinevo.core.org;

import java.io.Serial;

/**
 * The work location use cases (W-14.1, spec section 4), plus the one failure the other two masters
 * cannot have.
 */
public interface WorkLocationService extends OrgMasterService<WorkLocationRequest, WorkLocationResponse> {

    /**
     * This tenant already has a filing address and a second one was asked for. Maps to {@code 409}.
     *
     * <p><strong>Setting a new filing address is refused, not moved.</strong> The alternative was to
     * clear the flag on the existing location automatically, and it was rejected: a filing address is
     * what statutory registrations hang off (W-31), so one request about location B would silently
     * change location A's meaning, and nothing in the response would say so. Refusing makes the
     * caller do it in two steps it can see — clear the flag on the old location, then set it on the
     * new one. The partial unique index at {@code V013__work_location.sql} says the same thing one
     * layer down; this exception is so the caller gets a sentence instead of a constraint name.
     */
    class FilingAddressAlreadySetException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public FilingAddressAlreadySetException(String existingCode) {
            super("This tenant already files from work location " + existingCode
                    + ". Clear the filing flag there first — a filing address is not moved implicitly,"
                    + " because statutory registrations are tied to it.");
        }

        /**
         * The same failure raised by the partial unique index rather than by the service's own check,
         * which happens only when two requests race. The existing location's code is not read a
         * second time to name it: the transaction is already doomed at this point.
         */
        public FilingAddressAlreadySetException() {
            super("This tenant already has a filing address. Clear the filing flag there first — a"
                    + " filing address is not moved implicitly, because statutory registrations are"
                    + " tied to it.");
        }
    }
}
