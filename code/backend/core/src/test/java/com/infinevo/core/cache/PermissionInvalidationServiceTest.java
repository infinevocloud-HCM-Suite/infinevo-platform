package com.infinevo.core.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionInvalidationServiceTest {

    private PermissionCacheService permissionCacheService;
    private PermissionInvalidationService invalidationService;

    @BeforeEach
    void setUp() {
        permissionCacheService = mock(PermissionCacheService.class);
        invalidationService = new PermissionInvalidationService(permissionCacheService);
    }

    @Test
    @DisplayName("invalidateUser calls evictUserPermissions")
    void invalidateUserCallsEvict() {
        UUID tenantId = UUID.randomUUID();
        invalidationService.invalidateUser(tenantId, "user-42");
        verify(permissionCacheService).evictUserPermissions(tenantId, "user-42");
    }

    @Test
    @DisplayName("invalidateRoleUsers evicts each user associated with role")
    void invalidateRoleUsersEvictsAllUsers() {
        UUID tenantId = UUID.randomUUID();
        List<String> users = List.of("u1", "u2", "u3");

        invalidationService.invalidateRoleUsers(tenantId, "role-admin", users);
        verify(permissionCacheService).evictUserPermissions(tenantId, "u1");
        verify(permissionCacheService).evictUserPermissions(tenantId, "u2");
        verify(permissionCacheService).evictUserPermissions(tenantId, "u3");
    }

    @Test
    @DisplayName("invalidateAllTenantPermissions calls evictTenantPermissions")
    void invalidateAllCallsEvict() {
        UUID tenantId = UUID.randomUUID();
        invalidationService.invalidateAllTenantPermissions(tenantId);
        verify(permissionCacheService).evictTenantPermissions(tenantId);
    }
}
