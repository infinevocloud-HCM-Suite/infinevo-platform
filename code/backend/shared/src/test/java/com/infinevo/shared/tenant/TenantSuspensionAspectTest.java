package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.entitlement.EntitlementDeniedException;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.impersonation.ActingAs;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit tests for {@link TenantSuspensionAspect} (W-85.1 §7).
 */
class TenantSuspensionAspectTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID platformTenantId = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;

    private EntitlementService entitlementService;
    private PlatformTenant platformTenant;
    private TenantSuspensionAspect aspect;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);
        platformTenant = new PlatformTenant(platformTenantId);
        aspect = new TenantSuspensionAspect(entitlementService, platformTenant, true);
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ActingAs.clear();
    }

    @Test
    @DisplayName("GET passes even when tenant is suspended")
    void getPassesWhenSuspended() {
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), true));

        SampleController controller = proxy(new SampleController());
        assertThat(controller.read()).isEqualTo("read");
    }

    @Test
    @DisplayName("POST, PUT, PATCH, DELETE are refused with TENANT_SUSPENDED when suspended")
    void mutatingMethodsRefusedWhenSuspended() {
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), true));

        SampleController controller = proxy(new SampleController());

        assertThatThrownBy(controller::create)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));

        assertThatThrownBy(controller::update)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));

        assertThatThrownBy(controller::patchMethod)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));

        assertThatThrownBy(controller::deleteMethod)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));
    }

    @Test
    @DisplayName("When not suspended, mutating methods pass")
    void mutatingMethodsPassWhenActive() {
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), false));

        SampleController controller = proxy(new SampleController());
        assertThatCode(controller::create).doesNotThrowAnyException();
        assertThatCode(controller::update).doesNotThrowAnyException();
        assertThatCode(controller::patchMethod).doesNotThrowAnyException();
        assertThatCode(controller::deleteMethod).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("No tenant bound passes")
    void noTenantBoundPasses() {
        TenantContext.clear();
        SampleController controller = proxy(new SampleController());
        assertThatCode(controller::create).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Platform tenant passes even if suspended")
    void platformTenantPasses() {
        TenantContext.set(platformTenantId);
        SampleController controller = proxy(new SampleController());
        assertThatCode(controller::create).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ActingAs impersonation passes even when customer is suspended")
    void actingAsPasses() {
        ActingAs.set(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "test@user.com", Set.of());
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), true));

        SampleController controller = proxy(new SampleController());
        assertThatCode(controller::create).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@AllowedWhenSuspended passes even when suspended")
    void allowedWhenSuspendedPasses() {
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), true));

        SampleController controller = proxy(new SampleController());
        assertThat(controller.export()).isEqualTo("export");
    }

    @Test
    @DisplayName("Snapshot load failure refuses (fail-closed)")
    void snapshotLoadFailureRefuses() {
        when(entitlementService.snapshotOf(tenant)).thenThrow(new RuntimeException("Redis connection error"));

        SampleController controller = proxy(new SampleController());
        assertThatThrownBy(controller::create)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));
    }

    @Test
    @DisplayName("Null snapshot refuses (fail-closed)")
    void nullSnapshotRefuses() {
        when(entitlementService.snapshotOf(tenant)).thenReturn(null);

        SampleController controller = proxy(new SampleController());
        assertThatThrownBy(controller::create)
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));
    }

    @Test
    @DisplayName("Through MockMvc: returns 403 TENANT_SUSPENDED in standard envelope")
    void mockMvcReturns403() throws Exception {
        when(entitlementService.snapshotOf(tenant)).thenReturn(new EntitlementSnapshot(Set.of(), Set.of(), true));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(proxy(new SampleController()))
                .setControllerAdvice(new AuthzExceptionHandler())
                .build();

        mvc.perform(get("/sample")).andExpect(status().isOk());

        mvc.perform(post("/sample"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        mvc.perform(put("/sample"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        mvc.perform(patch("/sample"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        mvc.perform(delete("/sample"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        mvc.perform(post("/sample/export")).andExpect(status().isOk());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    @RestController
    @RequestMapping("/sample")
    static class SampleController {

        @GetMapping
        public String read() {
            return "read";
        }

        @PostMapping
        public ResponseEntity<String> create() {
            return ResponseEntity.ok("create");
        }

        @PutMapping
        public ResponseEntity<String> update() {
            return ResponseEntity.ok("update");
        }

        @PatchMapping
        public ResponseEntity<String> patchMethod() {
            return ResponseEntity.ok("patch");
        }

        @DeleteMapping
        public ResponseEntity<String> deleteMethod() {
            return ResponseEntity.ok("delete");
        }

        @PostMapping("/export")
        @AllowedWhenSuspended
        public String export() {
            return "export";
        }
    }
}
