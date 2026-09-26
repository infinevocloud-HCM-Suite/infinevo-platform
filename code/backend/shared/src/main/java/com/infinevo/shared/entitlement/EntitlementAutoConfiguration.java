package com.infinevo.shared.entitlement;

import com.infinevo.shared.authz.PermissionCache;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for entitlement enforcement (W-12.2).
 */
@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class EntitlementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntitlementService entitlementService(
            ObjectProvider<PermissionCache> permissionCache, ObjectProvider<EntitlementSource> entitlementSource) {
        return new EntitlementService(permissionCache::getIfAvailable, entitlementSource::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequiresModuleAspect requiresModuleAspect(EntitlementService entitlementService) {
        return new RequiresModuleAspect(entitlementService);
    }
}
