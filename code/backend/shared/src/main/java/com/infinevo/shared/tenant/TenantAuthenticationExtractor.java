package com.infinevo.shared.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts user identity and target tenant UUID from HTTP requests and authentication principals.
 */
public interface TenantAuthenticationExtractor {

    record TenantExtractionResult(UUID userId, Optional<UUID> tenantId, boolean tenantProvidedExplicitly) {}

    TenantExtractionResult extract(HttpServletRequest request, Authentication authentication);

    class DefaultTenantAuthenticationExtractor implements TenantAuthenticationExtractor {

        private static final String HEADER_X_TENANT_ID = "X-Tenant-Id";
        private static final String CLAIM_TENANT_ID = "tenant_id";
        private static final String CLAIM_TID = "tid";

        @Override
        public TenantExtractionResult extract(HttpServletRequest request, Authentication authentication) {
            UUID userId = extractUserId(authentication);
            Optional<UUID> tenantFromHeader = extractHeaderTenant(request);
            Optional<UUID> tenantFromJwt = extractJwtTenant(authentication);

            Optional<UUID> tenantId = tenantFromHeader.or(() -> tenantFromJwt);
            boolean explicit = tenantFromHeader.isPresent() || tenantFromJwt.isPresent();

            return new TenantExtractionResult(userId, tenantId, explicit);
        }

        private UUID extractUserId(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new IllegalArgumentException("Authentication required to extract user identity");
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String subject = jwt.getSubject();
                if (subject != null && !subject.isBlank()) {
                    try {
                        return UUID.fromString(subject);
                    } catch (IllegalArgumentException ignored) {
                        // Fallback to name if subject is not UUID
                    }
                }
            }
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Authenticated principal name is not a valid UUID: " + authentication.getName(), e);
            }
        }

        private Optional<UUID> extractHeaderTenant(HttpServletRequest request) {
            if (request == null) {
                return Optional.empty();
            }
            String headerVal = request.getHeader(HEADER_X_TENANT_ID);
            if (headerVal == null || headerVal.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(headerVal.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid X-Tenant-Id header format (expected UUID): " + headerVal, e);
            }
        }

        private Optional<UUID> extractJwtTenant(Authentication authentication) {
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                return Optional.empty();
            }
            String claim = jwt.getClaimAsString(CLAIM_TENANT_ID);
            if (claim == null || claim.isBlank()) {
                claim = jwt.getClaimAsString(CLAIM_TID);
            }
            if (claim == null || claim.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(claim.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid JWT tenant claim format (expected UUID): " + claim, e);
            }
        }
    }
}
