package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static com.infinevo.shared.authz.AuthzTestSupport.identityResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-11.2 — {@link RequiresActionAspect} lets a held action through, stops an unheld one before the
 * method body runs, and the refusal reaches the client as {@code 403} in the shared envelope.
 */
class RequiresActionAspectTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private StubActionSource source;
    private PermissionCache cache;
    private RequiresActionAspect aspect;

    @BeforeEach
    void setUp() {
        source = new StubActionSource();
        source.grant(tenant, user, "core.employee.read", "core.org.read");
        cache = new PermissionCache(new FakeCacheService());
        aspect = new RequiresActionAspect(new PermissionService(() -> cache, () -> source, identityResolver()));
        TenantContext.set(tenant);
        authenticate(user);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A held action: the method runs and returns normally")
    void heldActionProceeds() {
        GuardedBean bean = proxy(new GuardedBean());

        assertThat(bean.read()).isEqualTo("read");
    }

    @Test
    @DisplayName("An unheld action: PermissionDeniedException, and the method body never runs")
    void unheldActionStopsBeforeTheBody() {
        GuardedBean target = new GuardedBean();
        GuardedBean bean = proxy(target);

        assertThatThrownBy(bean::delete)
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("core.employee.delete");
        assertThat(target.deletes.get()).isZero();
    }

    @Test
    @DisplayName("A type-level annotation guards every method; a method-level one overrides it")
    void typeLevelAnnotation() {
        GuardedByType bean = proxy(new GuardedByType());

        assertThat(bean.inherited()).isEqualTo("org");
        assertThatThrownBy(bean::overridden)
                .as("core.org.read is held, but the method asks for core.org.manage")
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Through MVC: 200 when held, 403 with the ApiErrorResponse envelope when not")
    void mvcAnswers403InTheSharedEnvelope() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(proxy(new GuardedController()))
                .setControllerAdvice(new AuthzExceptionHandler())
                .build();

        mvc.perform(get("/probe/read")).andExpect(status().isOk());

        mvc.perform(get("/probe/delete"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Not permitted: requires action 'core.employee.delete'"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    @DisplayName("Through MVC with no tenant bound: 403, not 200")
    void mvcUnboundTenantIs403() throws Exception {
        TenantContext.clear();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(proxy(new GuardedController()))
                .setControllerAdvice(new AuthzExceptionHandler())
                .build();

        mvc.perform(get("/probe/read")).andExpect(status().isForbidden());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("An alternative action in anyOf: proceeds if held, throws if neither is held")
    void anyOfActionProceedsWhenHeld() {
        source.grant(tenant, user, "core.employee.update_own");
        cache.bumpVersion(tenant);
        GuardedBean bean = proxy(new GuardedBean());

        assertThat(bean.updateWithAnyOf()).isEqualTo("updated");

        source.grant(tenant, user);
        cache.bumpVersion(tenant);
        assertThatThrownBy(bean::updateWithAnyOf)
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("core.employee.update");
    }

    static class GuardedBean {
        final AtomicInteger deletes = new AtomicInteger();

        @RequiresAction("core.employee.read")
        public String read() {
            return "read";
        }

        @RequiresAction("core.employee.delete")
        public String delete() {
            deletes.incrementAndGet();
            return "deleted";
        }

        @RequiresAction(value = "core.employee.update", anyOf = "core.employee.update_own")
        public String updateWithAnyOf() {
            return "updated";
        }
    }

    @RequiresAction("core.org.read")
    static class GuardedByType {
        public String inherited() {
            return "org";
        }

        @RequiresAction("core.org.manage")
        public String overridden() {
            return "managed";
        }
    }

    @RestController
    static class GuardedController {
        @GetMapping("/probe/read")
        @RequiresAction("core.employee.read")
        public String read() {
            return "read";
        }

        @GetMapping("/probe/delete")
        @RequiresAction("core.employee.delete")
        public String delete() {
            return "deleted";
        }
    }
}
