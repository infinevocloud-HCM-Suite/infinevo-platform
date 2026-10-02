package com.infinevo.hrms.project;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the authenticated caller identifier for audit columns (W-41).
 */
final class ProjectActor {

    static final String ACTOR_SYSTEM = "system";
    private static final int MAX_ACTOR_LENGTH = 100;

    private ProjectActor() {}

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LENGTH ? name.substring(0, MAX_ACTOR_LENGTH) : name;
    }
}
