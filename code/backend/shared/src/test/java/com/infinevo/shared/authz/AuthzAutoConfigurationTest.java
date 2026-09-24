package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static com.infinevo.shared.authz.AuthzTestSupport.identityResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * W-11.2 — {@link AuthzAutoConfiguration} wires the check into an ordinary Spring context: a bean with
 * {@link RequiresAction} is proxied and guarded, and a context missing its collaborators still starts
 * and refuses.
 */
class AuthzAutoConfigurationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, AuthzAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A @RequiresAction bean is proxied; held passes, unheld is refused")
    void guardsAnnotatedBeans() {
        runner.withUserConfiguration(FullConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(PermissionService.class).hasSingleBean(RequiresActionAspect.class);
            Probe probe = ctx.getBean(Probe.class);
            assertThat(AopUtils.isAopProxy(probe)).isTrue();

            TenantContext.set(TENANT);
            authenticate(USER);
            assertThat(probe.read()).isEqualTo("ok");
            assertThatThrownBy(probe::delete).isInstanceOf(PermissionDeniedException.class);
        });
    }

    @Test
    @DisplayName("No cache, no ActionSource, no identity service: the context starts and every check refuses")
    void startsAndRefusesWithoutCollaborators() {
        runner.withUserConfiguration(ProbeOnly.class).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(PermissionCache.class);

            TenantContext.set(TENANT);
            authenticate(USER);
            assertThatThrownBy(ctx.getBean(Probe.class)::read).isInstanceOf(PermissionDeniedException.class);
        });
    }

    @Test
    @DisplayName("The 403 handler is registered in a servlet web application, and not outside one")
    void exceptionHandlerOnlyInWebApplications() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, AuthzAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).hasSingleBean(AuthzExceptionHandler.class));
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(AuthzExceptionHandler.class));
    }

    static class Probe {
        @RequiresAction("core.employee.read")
        public String read() {
            return "ok";
        }

        @RequiresAction("core.employee.delete")
        public String delete() {
            return "deleted";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeOnly {
        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FullConfig {
        @Bean
        Probe probe() {
            return new Probe();
        }

        @Bean
        CacheService cacheService() {
            return new FakeCacheService();
        }

        @Bean
        ActionSource actionSource() {
            StubActionSource source = new StubActionSource();
            source.grant(TENANT, USER, "core.employee.read");
            return source;
        }

        @Bean
        UserAccountIdResolver userAccountIdResolver() {
            return identityResolver();
        }
    }
}
