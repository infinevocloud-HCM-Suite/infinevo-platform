package com.infinevo.core.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantOptionServiceImplTest {

    private final UUID tenantId = UUID.randomUUID();
    private TenantOptionRegistry registry;
    private TenantOptionRepository repository;
    private CacheService cacheService;
    private ObjectMapper objectMapper;
    private TenantOptionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        registry = new TenantOptionRegistry(null, null, null);
        repository = mock(TenantOptionRepository.class);
        cacheService = mock(CacheService.class);
        objectMapper = new ObjectMapper();

        // Sample switches
        registry.register(OptionDefinition.of(
                "core.example.sample_switch",
                OptionValueType.BOOLEAN,
                OptionCategory.GENERAL,
                "false",
                "Sample switch",
                "Test switch description"));
        registry.register(OptionDefinition.of(
                "core.example.max_items",
                OptionValueType.INTEGER,
                OptionCategory.GENERAL,
                "10",
                "Max items",
                "Test int description",
                1,
                50));
        registry.register(OptionDefinition.of(
                "core.example.greeting",
                OptionValueType.STRING,
                OptionCategory.GENERAL,
                "hello",
                "Greeting",
                "Test str description"));
        registry.register(OptionDefinition.of(
                "core.example.config",
                OptionValueType.JSON,
                OptionCategory.GENERAL,
                "{\"active\":true}",
                "Config",
                "Test json description"));

        // Default getOrCompute mock behaviour: directly invokes supplier
        when(cacheService.getOrCompute(any(), eq(String.class), any(Duration.class), any()))
                .thenAnswer(inv -> {
                    Supplier<String> supplier = inv.getArgument(3);
                    return supplier.get();
                });

        service = new TenantOptionServiceImpl(registry, repository, cacheService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Default value is returned when no database override exists")
    void defaultReturnedWhenNoDbRow() {
        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.sample_switch"))
                .thenReturn(Optional.empty());

        boolean val = service.getBoolean("core.example.sample_switch");
        assertThat(val).isFalse();
    }

    @Test
    @DisplayName("Overridden database value is returned by typed getters")
    void overriddenValueReturned() {
        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.sample_switch"))
                .thenReturn(Optional.of(new TenantOption(tenantId, "core.example.sample_switch", "true")));
        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.max_items"))
                .thenReturn(Optional.of(new TenantOption(tenantId, "core.example.max_items", "25")));
        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.greeting"))
                .thenReturn(Optional.of(new TenantOption(tenantId, "core.example.greeting", "welcome")));

        assertThat(service.getBoolean("core.example.sample_switch")).isTrue();
        assertThat(service.getInteger("core.example.max_items")).isEqualTo(25);
        assertThat(service.getString("core.example.greeting")).isEqualTo("welcome");
    }

    @Test
    @DisplayName("getJson parses JSON object correctly")
    void getJsonParsesObject() {
        record SampleConfig(boolean active) {}
        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.config"))
                .thenReturn(Optional.of(new TenantOption(tenantId, "core.example.config", "{\"active\":false}")));

        SampleConfig cfg = service.getJson("core.example.config", SampleConfig.class);
        assertThat(cfg.active()).isFalse();
    }

    @Test
    @DisplayName("Unknown option key throws NoSuchOptionException in all getters")
    void unknownKeyThrows() {
        assertThatThrownBy(() -> service.getBoolean("non.existent.key"))
                .isInstanceOf(TenantOptionService.NoSuchOptionException.class);
        assertThatThrownBy(() -> service.getString("non.existent.key"))
                .isInstanceOf(TenantOptionService.NoSuchOptionException.class);
        assertThatThrownBy(() -> service.getInteger("non.existent.key"))
                .isInstanceOf(TenantOptionService.NoSuchOptionException.class);
        assertThatThrownBy(() -> service.getJson("non.existent.key", Object.class))
                .isInstanceOf(TenantOptionService.NoSuchOptionException.class);
    }

    @Test
    @DisplayName("set() validates type and allowed range")
    void setValidatesTypeAndRange() {
        // Boolean validation
        assertThatThrownBy(() -> service.set("core.example.sample_switch", "maybe"))
                .isInstanceOf(TenantOptionService.OptionValidationException.class);

        // Integer range validation
        assertThatThrownBy(() -> service.set("core.example.max_items", "0"))
                .isInstanceOf(TenantOptionService.OptionValidationException.class)
                .hasMessageContaining("below minimum");

        assertThatThrownBy(() -> service.set("core.example.max_items", "999"))
                .isInstanceOf(TenantOptionService.OptionValidationException.class)
                .hasMessageContaining("above maximum");
    }

    @Test
    @DisplayName("set() and reset() evict the cache and save/delete row")
    void setAndResetEvictCache() {
        String cacheKey = TenantCacheKeyGenerator.tenantKey(tenantId, "tenant-option", "core.example.sample_switch");

        when(repository.findByTenantIdAndOptionKey(tenantId, "core.example.sample_switch"))
                .thenReturn(Optional.empty());

        TenantOptionResponse setResp = service.set("core.example.sample_switch", "true");
        assertThat(setResp.value()).isEqualTo("true");
        assertThat(setResp.overridden()).isTrue();
        verify(repository).save(any(TenantOption.class));

        TenantOptionResponse resetResp = service.reset("core.example.sample_switch");
        assertThat(resetResp.value()).isEqualTo("false");
        assertThat(resetResp.overridden()).isFalse();
        verify(repository).deleteByTenantIdAndOptionKey(tenantId, "core.example.sample_switch");
        verify(cacheService, org.mockito.Mockito.times(2)).evict(cacheKey);
    }

    @Test
    @DisplayName("list() returns all options with effective value and overridden flag")
    void listReturnsAllOptions() {
        when(repository.findByTenantId(tenantId))
                .thenReturn(List.of(new TenantOption(tenantId, "core.example.max_items", "30")));

        List<TenantOptionResponse> list = service.list(null);
        assertThat(list).hasSize(4);

        TenantOptionResponse maxItems = list.stream()
                .filter(o -> o.key().equals("core.example.max_items"))
                .findFirst()
                .orElseThrow();
        assertThat(maxItems.value()).isEqualTo("30");
        assertThat(maxItems.overridden()).isTrue();

        TenantOptionResponse sampleSwitch = list.stream()
                .filter(o -> o.key().equals("core.example.sample_switch"))
                .findFirst()
                .orElseThrow();
        assertThat(sampleSwitch.value()).isEqualTo("false");
        assertThat(sampleSwitch.overridden()).isFalse();
    }
}
