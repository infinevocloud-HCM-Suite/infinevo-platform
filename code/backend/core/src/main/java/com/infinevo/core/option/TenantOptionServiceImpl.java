package com.infinevo.core.option;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TenantOptionService} backed by {@link TenantOptionRegistry},
 * {@link TenantOptionRepository}, and {@link CacheService} (W-104 §4).
 */
@Service
@Transactional
public class TenantOptionServiceImpl implements TenantOptionService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_DOMAIN = "tenant-option";

    private final TenantOptionRegistry registry;
    private final TenantOptionRepository repository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public TenantOptionServiceImpl(
            TenantOptionRegistry registry,
            TenantOptionRepository repository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolean(String key) {
        OptionDefinition def = requireDefinition(key);
        String raw = getRaw(def);
        return Boolean.parseBoolean(raw);
    }

    @Override
    @Transactional(readOnly = true)
    public String getString(String key) {
        OptionDefinition def = requireDefinition(key);
        return getRaw(def);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getInteger(String key) {
        OptionDefinition def = requireDefinition(key);
        String raw = getRaw(def);
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new OptionValidationException("Invalid integer option value: " + raw);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public <T> T getJson(String key, Class<T> targetClass) {
        OptionDefinition def = requireDefinition(key);
        String raw = getRaw(def);
        try {
            return objectMapper.readValue(raw, targetClass);
        } catch (Exception e) {
            throw new OptionValidationException("Failed to parse JSON option " + key + ": " + e.getMessage());
        }
    }

    @Override
    public TenantOptionResponse set(String key, String rawValue) {
        OptionDefinition def = requireDefinition(key);
        if (rawValue == null) {
            throw new OptionValidationException("Value cannot be null");
        }
        try {
            def.type().validate(rawValue, def);
        } catch (IllegalArgumentException e) {
            throw new OptionValidationException(e.getMessage());
        }

        UUID tenantId = TenantContext.require();
        TenantOption option = repository
                .findByTenantIdAndOptionKey(tenantId, key)
                .orElseGet(() -> new TenantOption(tenantId, key, rawValue));
        option.setOptionValue(rawValue);
        repository.save(option);

        cacheService.evict(cacheKey(tenantId, key));
        return toResponse(def, rawValue, true);
    }

    @Override
    public TenantOptionResponse reset(String key) {
        OptionDefinition def = requireDefinition(key);
        UUID tenantId = TenantContext.require();

        repository.deleteByTenantIdAndOptionKey(tenantId, key);
        cacheService.evict(cacheKey(tenantId, key));

        return toResponse(def, def.defaultValue(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantOptionResponse> list(OptionCategory category) {
        UUID tenantId = TenantContext.require();
        List<OptionDefinition> defs = registry.byCategory(category);
        Map<String, String> overrides = repository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantOption::getOptionKey, TenantOption::getOptionValue, (a, b) -> b));

        return defs.stream()
                .map(d -> {
                    String val = overrides.get(d.key());
                    boolean overridden = (val != null);
                    String effective = overridden ? val : d.defaultValue();
                    return toResponse(d, effective, overridden);
                })
                .toList();
    }

    private OptionDefinition requireDefinition(String key) {
        return registry.get(key).orElseThrow(() -> new NoSuchOptionException(key));
    }

    private String getRaw(OptionDefinition def) {
        UUID tenantId = TenantContext.require();
        String key = cacheKey(tenantId, def.key());
        return cacheService.getOrCompute(key, String.class, CACHE_TTL, () -> repository
                .findByTenantIdAndOptionKey(tenantId, def.key())
                .map(TenantOption::getOptionValue)
                .orElse(def.defaultValue()));
    }

    private String cacheKey(UUID tenantId, String optionKey) {
        return TenantCacheKeyGenerator.tenantKey(tenantId, CACHE_DOMAIN, optionKey);
    }

    private TenantOptionResponse toResponse(OptionDefinition def, String value, boolean overridden) {
        return new TenantOptionResponse(
                def.key(),
                def.category(),
                def.type(),
                def.label(),
                def.description(),
                value,
                def.defaultValue(),
                overridden);
    }
}
