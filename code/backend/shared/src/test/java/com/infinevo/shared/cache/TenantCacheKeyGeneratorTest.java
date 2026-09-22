package com.infinevo.shared.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantCacheKeyGeneratorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Generates tenant key from bound TenantContext")
    void generatesTenantKeyFromContext() {
        UUID tenantId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        TenantContext.set(tenantId);

        String key = TenantCacheKeyGenerator.tenantKey("auth:perm", "user-123");
        assertEquals("infinevo:6ba7b810-9dad-11d1-80b4-00c04fd430c8:auth:perm:user-123", key);
    }

    @Test
    @DisplayName("Throws IllegalStateException when no TenantContext is bound")
    void throwsWhenNoTenantContextBound() {
        assertThrows(IllegalStateException.class, () -> TenantCacheKeyGenerator.tenantKey("auth:perm", "user-123"));
    }

    @Test
    @DisplayName("Generates tenant key from explicit tenant UUID")
    void generatesTenantKeyFromExplicitUuid() {
        UUID tenantId = UUID.randomUUID();
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, "config", "settings");
        assertEquals("infinevo:" + tenantId + ":config:settings", key);
    }

    @Test
    @DisplayName("Generates tenant domain pattern for bulk invalidation")
    void generatesTenantDomainPattern() {
        UUID tenantId = UUID.randomUUID();
        String pattern = TenantCacheKeyGenerator.tenantDomainPattern(tenantId, "auth:perm");
        assertEquals("infinevo:" + tenantId + ":auth:perm:*", pattern);
    }

    @Test
    @DisplayName("Generates global reference cache key")
    void generatesGlobalKey() {
        String key = TenantCacheKeyGenerator.globalKey("ref:tax_slab", "2026-2027:NEW");
        assertEquals("infinevo:global:ref:tax_slab:2026-2027:NEW", key);
    }

    @Test
    @DisplayName("Rejects null parameters with NullPointerException")
    void rejectsNullParameters() {
        UUID tenantId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> TenantCacheKeyGenerator.tenantKey(null, "domain", "key"));
        assertThrows(NullPointerException.class, () -> TenantCacheKeyGenerator.tenantKey(tenantId, null, "key"));
        assertThrows(NullPointerException.class, () -> TenantCacheKeyGenerator.tenantKey(tenantId, "domain", null));
        assertThrows(NullPointerException.class, () -> TenantCacheKeyGenerator.globalKey(null, "key"));
        assertThrows(NullPointerException.class, () -> TenantCacheKeyGenerator.globalKey("domain", null));
    }
}
