package com.infinevo.core.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The cache adapter with and without a cache: with {@code infinevo.cache.enabled=false} there is no
 * {@link CacheService} bean, and the adapter must still be built and must answer as a miss.
 */
class MasterDataCacheServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("With no cache configured, reads miss and writes and evictions do nothing")
    @SuppressWarnings("unchecked")
    void noCacheConfigured() {
        ObjectProvider<CacheService> none = mock(ObjectProvider.class);
        when(none.getIfAvailable()).thenReturn(null);
        MasterDataCacheService service = new MasterDataCacheService(none);

        assertThat(service.getTenantMasterData(tenantId, "department", "d1", String.class))
                .isEmpty();
        assertThat(service.getGlobalReferenceData("tax_slab", "2026-2027", String.class))
                .isEmpty();
        service.putTenantMasterData(tenantId, "department", "d1", "Engineering");
        service.putGlobalReferenceData("tax_slab", "2026-2027", "slabs");
        service.evictTenantMasterData(tenantId, "department", "d1");
        service.evictGlobalReferenceData("tax_slab", "2026-2027");
    }

    @Test
    @DisplayName("With a cache, tenant data is keyed by tenant and kept for the default two hours")
    @SuppressWarnings("unchecked")
    void cacheConfigured() {
        CacheService cache = mock(CacheService.class);
        ObjectProvider<CacheService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cache);
        MasterDataCacheService service = new MasterDataCacheService(provider);
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, "master:department", "d1");
        when(cache.get(key, String.class)).thenReturn(Optional.of("Engineering"));

        service.putTenantMasterData(tenantId, "department", "d1", "Engineering");

        verify(cache).put(key, "Engineering", Duration.ofHours(2));
        assertThat(service.getTenantMasterData(tenantId, "department", "d1", String.class))
                .contains("Engineering");
    }
}
