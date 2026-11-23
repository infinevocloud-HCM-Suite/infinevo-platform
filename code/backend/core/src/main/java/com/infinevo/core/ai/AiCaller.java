package com.infinevo.core.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The signed-in user's id for the AI helper (W-114.3): the token subject, the id the platform keys a user on
 * ({@code shared/identity/UserProfileSyncFilter.java:103-118}). Read the way {@code EmployeeServiceImpl.currentEmployee}
 * reads it ({@code core/employee/EmployeeServiceImpl.java:308-319}); empty for no user, an anonymous one, or a subject
 * that is not a UUID.
 */
final class AiCaller {

    private AiCaller() {}

    static Optional<UUID> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String subject = auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : auth.getName();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
