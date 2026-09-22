package com.infinevo.core.identity.controller;

import com.infinevo.core.identity.dto.UserAccountResponse;
import com.infinevo.core.identity.entity.UserAccount;
import com.infinevo.core.identity.service.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing user profile identity REST endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileSyncService userProfileSyncService;

    public UserController(UserProfileSyncService userProfileSyncService) {
        this.userProfileSyncService =
                Objects.requireNonNull(userProfileSyncService, "userProfileSyncService must not be null");
    }

    /**
     * Retrieves the profile of the currently authenticated Keycloak user, synchronizing profile data to core.user_account.
     */
    @GetMapping("/me")
    public ResponseEntity<UserAccountResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return ResponseEntity.status(401).build();
        }

        UUID tenantId = TenantContext.require();
        UserAccount userAccount = userProfileSyncService.syncUserProfile(jwt, tenantId);

        return ResponseEntity.ok(UserAccountResponse.fromEntity(userAccount));
    }
}
