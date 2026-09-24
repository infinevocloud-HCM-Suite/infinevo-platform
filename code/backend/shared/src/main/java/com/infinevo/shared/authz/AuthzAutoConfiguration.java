package com.infinevo.shared.authz;

import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.identity.UserProfileSyncService;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the permission check (W-11.2): {@link PermissionCache}, {@link PermissionService}, the
 * {@link RequiresActionAspect} that enforces {@link RequiresAction}, and the {@code 403} handler.
 *
 * <p>Follows {@code TenantBindingAutoConfiguration}: listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, every bean
 * {@code @ConditionalOnMissingBean} so a module can replace one.
 *
 * <p>Nothing here requires another bean to exist. The cache, the {@link ActionSource} ({@code core})
 * and the identity service are looked up at call time through providers, so a context missing any of
 * them still starts — and refuses every {@link RequiresAction} check, which is the direction that is
 * safe. The proxying itself comes from Spring Boot's {@code AopAutoConfiguration}, on by default with
 * AspectJ on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class AuthzAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionCache permissionCache(ObjectProvider<CacheService> cacheService) {
        return new PermissionCache(cacheService::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserAccountIdResolver userAccountIdResolver(ObjectProvider<UserProfileSyncService> profiles) {
        return new UserAccountIdResolver.FromUserProfiles(profiles);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionService permissionService(
            ObjectProvider<PermissionCache> permissionCache,
            ObjectProvider<ActionSource> actionSource,
            UserAccountIdResolver userAccountIdResolver) {
        return new PermissionService(
                permissionCache::getIfAvailable, actionSource::getIfAvailable, userAccountIdResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequiresActionAspect requiresActionAspect(PermissionService permissionService) {
        return new RequiresActionAspect(permissionService);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AuthzExceptionHandler authzExceptionHandler() {
        return new AuthzExceptionHandler();
    }
}
