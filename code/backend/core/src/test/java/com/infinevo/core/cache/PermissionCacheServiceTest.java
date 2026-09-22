package com.infinevo.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.cache.CacheService;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionCacheServiceTest {

    private CacheService cacheService;
    private PermissionCacheService permissionCacheService;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        permissionCacheService = new PermissionCacheService(cacheService);
    }

    @Test
    @DisplayName("getUserPermissions returns cached permissions")
    void getUserPermissionsReturnsCached() {
        UUID tenantId = UUID.randomUUID();
        String userId = "user-1";
        Set<String> actions = Set.of("VIEW_PAYROLL", "APPROVE_LEAVE");

        when(cacheService.get("infinevo:" + tenantId + ":auth:perm:user-1", Set.class))
                .thenReturn(Optional.of(actions));

        Optional<Set<String>> result = permissionCacheService.getUserPermissions(tenantId, userId);
        assertTrue(result.isPresent());
        assertEquals(actions, result.get());
    }

    @Test
    @DisplayName("putUserPermissions stores actions with 15-minute TTL")
    void putUserPermissionsStoresWithTtl() {
        UUID tenantId = UUID.randomUUID();
        String userId = "user-2";
        Set<String> actions = Set.of("EDIT_PROFILE");

        permissionCacheService.putUserPermissions(tenantId, userId, actions);
        verify(cacheService).put("infinevo:" + tenantId + ":auth:perm:user-2", actions, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("evictUserPermissions deletes specific user permission key")
    void evictUserPermissionsDeletesKey() {
        UUID tenantId = UUID.randomUUID();
        String userId = "user-3";

        permissionCacheService.evictUserPermissions(tenantId, userId);
        verify(cacheService).evict("infinevo:" + tenantId + ":auth:perm:user-3");
    }

    @Test
    @DisplayName("evictTenantPermissions deletes all user keys in tenant")
    void evictTenantPermissionsDeletesPattern() {
        UUID tenantId = UUID.randomUUID();

        permissionCacheService.evictTenantPermissions(tenantId);
        verify(cacheService).evictPattern("infinevo:" + tenantId + ":auth:perm:*");
    }
}
