package com.itsdev.payroll.service.auth;

import java.util.Set;

public interface AuthzService {
    /**
     * Returns the set of action codes (action.code) allowed for the user in this
     * organization.
     */
    Set<String> getAllowedActionsForUser(String userId, String organizationId);

    /**
     * Check whether a user is allowed to perform a given actionKey in an org.
     */
    boolean canPerform(String userId, String organizationId, String actionKey);

    /**
     * Invalidate cache for a given user+org (call after role/action mapping
     * changes).
     */
    void invalidateUserOrgCache(String userId, String organizationId);
}
