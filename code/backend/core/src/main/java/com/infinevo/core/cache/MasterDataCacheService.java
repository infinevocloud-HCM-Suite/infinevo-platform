package com.infinevo.core.cache;

import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Domain cache adapter for tenant master data and global statutory reference data.
 */
@Service
public class MasterDataCacheService {

    private static final Duration DEFAULT_MASTER_TTL = Duration.ofHours(2);
    private static final Duration DEFAULT_GLOBAL_TTL = Duration.ofHours(24);

    private final CacheService cacheService;

    public MasterDataCacheService(CacheService cacheService) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService must not be null");
    }

    /**
     * Retrieves tenant-scoped master data.
     */
    public <T> Optional<T> getTenantMasterData(UUID tenantId, String domain, String id, Class<T> clazz) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, "master:" + domain, id);
        return cacheService.get(key, clazz);
    }

    /**
     * Caches tenant-scoped master data.
     */
    public <T> void putTenantMasterData(UUID tenantId, String domain, String id, T data) {
        putTenantMasterData(tenantId, domain, id, data, DEFAULT_MASTER_TTL);
    }

    /**
     * Caches tenant-scoped master data with custom TTL.
     */
    public <T> void putTenantMasterData(UUID tenantId, String domain, String id, T data, Duration ttl) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, "master:" + domain, id);
        cacheService.put(key, data, ttl != null ? ttl : DEFAULT_MASTER_TTL);
    }

    /**
     * Evicts tenant-scoped master data.
     */
    public void evictTenantMasterData(UUID tenantId, String domain, String id) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, "master:" + domain, id);
        cacheService.evict(key);
    }

    /**
     * Retrieves global (un-tenanted) statutory reference data.
     */
    public <T> Optional<T> getGlobalReferenceData(String domain, String lookupKey, Class<T> clazz) {
        String key = TenantCacheKeyGenerator.globalKey("ref:" + domain, lookupKey);
        return cacheService.get(key, clazz);
    }

    /**
     * Caches global statutory reference data.
     */
    public <T> void putGlobalReferenceData(String domain, String lookupKey, T data) {
        putGlobalReferenceData(domain, lookupKey, data, DEFAULT_GLOBAL_TTL);
    }

    /**
     * Caches global statutory reference data with custom TTL.
     */
    public <T> void putGlobalReferenceData(String domain, String lookupKey, T data, Duration ttl) {
        String key = TenantCacheKeyGenerator.globalKey("ref:" + domain, lookupKey);
        cacheService.put(key, data, ttl != null ? ttl : DEFAULT_GLOBAL_TTL);
    }

    /**
     * Evicts global statutory reference data.
     */
    public void evictGlobalReferenceData(String domain, String lookupKey) {
        String key = TenantCacheKeyGenerator.globalKey("ref:" + domain, lookupKey);
        cacheService.evict(key);
    }
}
